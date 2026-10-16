package com.neha.commercehub.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neha.commercehub.common.BadRequestException;
import com.neha.commercehub.common.InsufficientStockException;
import com.neha.commercehub.common.ServiceBusyException;
import com.neha.commercehub.common.NotFoundException;
import com.neha.commercehub.order.Order;
import com.neha.commercehub.order.OrderDtos.OrderResponse;
import com.neha.commercehub.order.OrderService;
import com.neha.commercehub.order.CheckoutLine;
import com.neha.commercehub.order.OrderStatus;
import com.neha.commercehub.payment.PaymentDtos.CheckoutResponse;
import com.neha.commercehub.payment.PaymentDtos.PaymentInfo;
import com.neha.commercehub.scaling.StockGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Checkout and payment.
 *
 * This class deliberately has no @Transactional of its own. Talking to the payment
 * company is a slow network call, and it must not happen while a database
 * transaction is open and holding locks on product rows. So each database step is
 * its own short transaction (in OrderService and PaymentRecords), with the network
 * call in between.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderService orderService;
    private final PaymentRecords paymentRecords;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;
    private final long paymentWindowMinutes;
    private final StockGate stockGate;
    private final Semaphore orderSlots;
    private final long orderSlotWaitMillis;

    public PaymentService(OrderService orderService,
                          PaymentRecords paymentRecords,
                          PaymentRepository paymentRepository,
                          PaymentGateway gateway,
                          ObjectMapper objectMapper,
                          @Value("${razorpay.webhook-secret}") String webhookSecret,
                          @Value("${app.payment.window-minutes}") long paymentWindowMinutes,
                          StockGate stockGate,
                          @Value("${app.checkout.max-concurrent-orders:8}") int maxConcurrentOrders,
                          @Value("${app.checkout.slot-wait-millis:10000}") long orderSlotWaitMillis) {

        this.orderService = orderService;
        this.paymentRecords = paymentRecords;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
        this.paymentWindowMinutes = paymentWindowMinutes;
        this.stockGate = stockGate;
        this.orderSlots = new Semaphore(maxConcurrentOrders, true);
        this.orderSlotWaitMillis = orderSlotWaitMillis;
    }

    /**
     * Step 1 of buying: create the order (stock is held), then open a payment with the gateway.
     * The amount comes from the order the server just built, never from the browser.
     */
    public CheckoutResponse checkout(String email, String shippingAddress) {

        // 1. Fast stock check in Redis (see StockGate). In a flash sale most customers are
        //    answered "sold out" here, without opening a database transaction at all.
        List<CheckoutLine> taken = new ArrayList<>();

        for (CheckoutLine line : orderService.checkoutLines(email)) {

            StockGate.Result result = stockGate.tryTake(
                    line.productId(), line.quantity(), () -> orderService.currentStock(line.productId()));

            if (result == StockGate.Result.NOT_ENOUGH) {
                giveBack(taken);
                throw new InsufficientStockException(line.productName());
            }

            if (result == StockGate.Result.TAKEN) {
                taken.add(line);
            }
        }

        // 2. The real order, with the exact stock update in the database.
        //    Bulkhead: only a few order transactions run at once on this server. Customers
        //    with the same hot product would only queue on the same row lock anyway, and
        //    while they wait HERE they hold no database connection, so connections stay
        //    free for everyone else (browsing, carts, the stock gate).
        OrderResponse order;

        try {
            if (!orderSlots.tryAcquire(orderSlotWaitMillis, TimeUnit.MILLISECONDS)) {
                throw new ServiceBusyException("Checkout is very busy right now. Please try again in a moment.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            giveBack(taken);
            throw new ServiceBusyException("Checkout was interrupted. Please try again.");
        } catch (RuntimeException ex) {
            giveBack(taken);
            throw ex;
        }

        try {
            order = orderService.checkout(email, shippingAddress);
        } catch (RuntimeException ex) {
            giveBack(taken);
            throw ex;
        } finally {
            orderSlots.release();
        }

        long amountPaise = order.total().movePointRight(2).longValueExact();

        try {

            String gatewayOrderId = gateway.createOrder(order.orderNumber(), amountPaise);
            Payment payment = paymentRecords.create(order.orderNumber(), gatewayOrderId, amountPaise);

            return new CheckoutResponse(order, toInfo(payment));

        } catch (RuntimeException ex) {

            // No way to pay means no order: cancel it so the stock is not left held
            log.error("Could not start payment for order {}: {}", order.orderNumber(), ex.getMessage());
            Order saved = orderService.requireOwnOrder(email, order.orderNumber());
            orderService.cancelUnpaidById(saved.getId());

            throw new BadRequestException("Payment could not be started, so the order was not placed. Please try again.");
        }
    }

    private void giveBack(List<CheckoutLine> lines) {

        for (CheckoutLine line : lines) {
            stockGate.giveBack(line.productId(), line.quantity());
        }
    }

    /** Lets the customer come back and pay an order that is still waiting. */
    public PaymentInfo paymentInfo(String email, String orderNumber) {

        Order order = orderService.requireOwnOrder(email, orderNumber);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("This order is not waiting for payment.");
        }

        return toInfo(findPayment(order));
    }

    /**
     * Step 2 of buying: the browser reports that the payment window succeeded.
     * Nothing the browser says is trusted until the signature has been checked.
     */
    public boolean verify(String email, String orderNumber,
                          String gatewayOrderId, String gatewayPaymentId, String signature) {

        Order order = orderService.requireOwnOrder(email, orderNumber);
        Payment payment = findPayment(order);

        if (!payment.getGatewayOrderId().equals(gatewayOrderId)) {
            throw new BadRequestException("This payment does not belong to this order.");
        }

        if (!Signatures.matches(gatewayOrderId + "|" + gatewayPaymentId, gateway.signingSecret(), signature)) {
            throw new BadRequestException("Payment could not be verified.");
        }

        if (!paymentRecords.confirm(payment.getId(), order.getId(), gatewayPaymentId)) {
            throw new BadRequestException(
                    "This order was cancelled before the payment arrived. The payment has been flagged for refund.");
        }

        return true;
    }

    /**
     * Demo mode only: stands in for the payment window. It builds the same signed
     * message a real gateway would send and runs it through the same verification.
     */
    public boolean demoConfirm(String email, String orderNumber) {

        if (!gateway.isDemo()) {
            throw new NotFoundException("Not found.");
        }

        Order order = orderService.requireOwnOrder(email, orderNumber);
        Payment payment = findPayment(order);

        String gatewayPaymentId = "demo_pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String signature = Signatures.hmacSha256Hex(
                payment.getGatewayOrderId() + "|" + gatewayPaymentId, gateway.signingSecret());

        return verify(email, orderNumber, payment.getGatewayOrderId(), gatewayPaymentId, signature);
    }

    /**
     * The gateway's own server-to-server notification ("webhook"). It arrives even if
     * the customer closes the browser right after paying, so it is the safety net.
     * The signature is computed over the exact text received.
     */
    public void handleWebhook(String rawBody, String signatureHeader) {

        if (!Signatures.matches(rawBody, webhookSecret, signatureHeader)) {
            throw new BadRequestException("Webhook signature is not valid.");
        }

        JsonNode root;

        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new BadRequestException("Webhook body is not valid JSON.");
        }

        String event = root.path("event").asText("");

        if (!event.equals("payment.captured") && !event.equals("order.paid")) {
            return;   // other events are acknowledged and ignored
        }

        JsonNode entity = root.path("payload").path("payment").path("entity");
        String gatewayOrderId = entity.path("order_id").asText("");
        String gatewayPaymentId = entity.path("id").asText("");
        long amount = entity.path("amount").asLong(-1);

        Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);

        if (payment == null || gatewayPaymentId.isEmpty()) {
            log.warn("Webhook for an unknown order {}: ignored.", gatewayOrderId);
            return;
        }

        if (amount != payment.getAmountPaise()) {
            log.warn("Webhook amount {} does not match expected {} for {}: ignored.",
                    amount, payment.getAmountPaise(), gatewayOrderId);
            return;
        }

        paymentRecords.confirm(payment.getId(), payment.getOrder().getId(), gatewayPaymentId);
    }

    /** Every minute: cancel orders that were never paid, so their stock goes back on sale. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void cancelUnpaidOrders() {
        expireOlderThan(LocalDateTime.now().minusMinutes(paymentWindowMinutes));
    }

    /** Returns how many orders were cancelled. Separate from the timer so tests can call it directly. */
    public int expireOlderThan(LocalDateTime createdBefore) {

        int cancelled = 0;

        for (Long orderId : orderService.unpaidOrderIdsOlderThan(createdBefore)) {
            if (paymentRecords.expire(orderId)) {
                cancelled++;
            }
        }

        if (cancelled > 0) {
            log.info("Cancelled {} unpaid order(s) and released their stock.", cancelled);
        }

        return cancelled;
    }

    private Payment findPayment(Order order) {
        return paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new NotFoundException("No payment found for this order."));
    }

    private PaymentInfo toInfo(Payment payment) {
        return new PaymentInfo(
                payment.getGatewayOrderId(),
                payment.getAmountPaise(),
                "INR",
                gateway.publicKey(),
                gateway.isDemo());
    }
}
