package com.neha.commercehub.payment;

import com.neha.commercehub.common.NotFoundException;
import com.neha.commercehub.order.Order;
import com.neha.commercehub.order.OrderRepository;
import com.neha.commercehub.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** The database side of payments. Each method is one small transaction. */
@Service
public class PaymentRecords {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecords.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public PaymentRecords(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          OrderService orderService) {

        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Transactional
    public Payment create(String orderNumber, String gatewayOrderId, long amountPaise) {

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found."));

        return paymentRepository.save(new Payment(order, gatewayOrderId, amountPaise));
    }

    /**
     * Confirms a payment exactly once, however many times it is reported.
     *
     * The browser and the gateway's own notification can both arrive for the same
     * payment, in either order. Only the first one changes anything; the rest find
     * the payment already recorded and do nothing.
     *
     * Returns true if the order is paid after this call.
     */
    @Transactional
    public boolean confirm(Long paymentId, Long orderId, String gatewayPaymentId) {

        int first = paymentRepository.recordPayment(
                paymentId, gatewayPaymentId, LocalDateTime.now(), PaymentStatus.CREATED, PaymentStatus.PAID);

        if (first == 0) {

            // Money arrived for an order that had already timed out and been cancelled.
            // Its stock was given back, so the order is not revived; the payment is flagged instead.
            int late = paymentRepository.recordPayment(
                    paymentId, gatewayPaymentId, LocalDateTime.now(), PaymentStatus.EXPIRED, PaymentStatus.NEEDS_REFUND);

            if (late == 1) {
                log.warn("Payment {} arrived after its order expired; flagged NEEDS_REFUND.", gatewayPaymentId);
                return false;
            }

            // Otherwise this is a repeat of a confirmation already handled
            return paymentRepository.findById(paymentId)
                    .map(payment -> payment.getStatus() == PaymentStatus.PAID)
                    .orElse(false);
        }

        if (orderService.markPaid(orderId)) {
            return true;
        }

        // The order was cancelled in the moment between the two steps above
        paymentRepository.changeStatus(paymentId, PaymentStatus.PAID, PaymentStatus.NEEDS_REFUND);
        log.warn("Payment {} arrived for an order that is no longer waiting; flagged NEEDS_REFUND.", gatewayPaymentId);
        return false;
    }

    /** Cancels an unpaid order that waited too long, and closes its payment. */
    @Transactional
    public boolean expire(Long orderId) {

        if (!orderService.cancelUnpaidById(orderId)) {
            return false;
        }

        paymentRepository.findByOrderId(orderId).ifPresent(payment ->
                paymentRepository.changeStatus(payment.getId(), PaymentStatus.CREATED, PaymentStatus.EXPIRED));

        return true;
    }
}
