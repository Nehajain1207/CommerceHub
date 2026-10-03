package com.neha.commercehub;

import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.common.BadRequestException;
import com.neha.commercehub.common.NotFoundException;
import com.neha.commercehub.payment.Payment;
import com.neha.commercehub.payment.PaymentDtos.CheckoutResponse;
import com.neha.commercehub.payment.PaymentRepository;
import com.neha.commercehub.payment.PaymentService;
import com.neha.commercehub.payment.PaymentStatus;
import com.neha.commercehub.payment.Signatures;
import com.neha.commercehub.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Payment rules. The tests run in demo mode (no Razorpay keys), where the signing
 * secret is known, so they can build both genuine and forged "payment succeeded" messages.
 */
@SpringBootTest
class PaymentServiceTest {

    private static final String ADDRESS = "12 MG Road, Bengaluru";
    private static final String SIGNING_SECRET = "demo-secret";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TestData data;

    private User user;
    private Product bottle;

    @BeforeEach
    void setUp() {
        data.clear();
        user = data.customer();
        bottle = data.product("Bottle", "750.00", 10);
        data.addToCart(user, bottle, 2);
    }

    // ---------- helpers ----------

    private CheckoutResponse checkout() {
        return paymentService.checkout(user.getEmail(), ADDRESS);
    }

    private static String genuineSignature(String gatewayOrderId, String gatewayPaymentId) {
        return Signatures.hmacSha256Hex(gatewayOrderId + "|" + gatewayPaymentId, SIGNING_SECRET);
    }

    private Payment paymentOf(CheckoutResponse checkout) {
        return paymentRepository.findByGatewayOrderId(checkout.payment().gatewayOrderId()).orElseThrow();
    }

    private static String webhookBody(String event, String gatewayOrderId, String gatewayPaymentId, long amount) {
        return "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"" + gatewayPaymentId + "\","
                + "\"order_id\":\"" + gatewayOrderId + "\","
                + "\"amount\":" + amount + "}}}}";
    }

    // ---------- checkout ----------

    @Test
    void checkout_createsAnUnpaidOrder_holdsStock_andOpensAPayment() {

        CheckoutResponse checkout = checkout();

        assertEquals("PENDING_PAYMENT", checkout.order().status());
        assertEquals(8, data.stockOf(bottle));

        // 2 x 750.00 rupees = 150000 paise, worked out by the server
        assertEquals(150_000, checkout.payment().amountPaise());
        assertEquals("INR", checkout.payment().currency());
        assertTrue(checkout.payment().demo());
        assertEquals(PaymentStatus.CREATED, paymentOf(checkout).getStatus());
    }

    // ---------- browser confirmation ----------

    @Test
    void verify_withAGenuineSignature_marksTheOrderPaid() {

        CheckoutResponse checkout = checkout();
        String gatewayOrderId = checkout.payment().gatewayOrderId();

        boolean paid = paymentService.verify(user.getEmail(), checkout.order().orderNumber(),
                gatewayOrderId, "pay_123", genuineSignature(gatewayOrderId, "pay_123"));

        assertTrue(paid);
        assertEquals("PLACED", data.statusOf(checkout.order().orderNumber()));
        assertEquals(PaymentStatus.PAID, paymentOf(checkout).getStatus());
        assertEquals("pay_123", paymentOf(checkout).getGatewayPaymentId());
        assertNotNull(paymentOf(checkout).getPaidAt());
    }

    @Test
    void verify_withAForgedSignature_isRefused_andTheOrderStaysUnpaid() {

        CheckoutResponse checkout = checkout();
        String gatewayOrderId = checkout.payment().gatewayOrderId();

        String forged = Signatures.hmacSha256Hex(gatewayOrderId + "|pay_123", "a-guessed-secret");

        assertThrows(BadRequestException.class,
                () -> paymentService.verify(user.getEmail(), checkout.order().orderNumber(),
                        gatewayOrderId, "pay_123", forged));
        assertThrows(BadRequestException.class,
                () -> paymentService.verify(user.getEmail(), checkout.order().orderNumber(),
                        gatewayOrderId, "pay_123", "not-a-signature"));

        assertEquals("PENDING_PAYMENT", data.statusOf(checkout.order().orderNumber()));
        assertEquals(PaymentStatus.CREATED, paymentOf(checkout).getStatus());
    }

    @Test
    void verify_refusesASignatureMadeForADifferentPayment() {

        CheckoutResponse checkout = checkout();
        String gatewayOrderId = checkout.payment().gatewayOrderId();

        // Genuine signature, but for pay_AAA; presented with pay_BBB
        assertThrows(BadRequestException.class,
                () -> paymentService.verify(user.getEmail(), checkout.order().orderNumber(),
                        gatewayOrderId, "pay_BBB", genuineSignature(gatewayOrderId, "pay_AAA")));

        assertEquals("PENDING_PAYMENT", data.statusOf(checkout.order().orderNumber()));
    }

    @Test
    void verify_refusesAPaymentThatBelongsToAnotherOrder() {

        CheckoutResponse first = checkout();

        User other = data.customer();
        data.addToCart(other, bottle, 1);
        CheckoutResponse second = paymentService.checkout(other.getEmail(), ADDRESS);

        String otherGatewayOrderId = second.payment().gatewayOrderId();

        assertThrows(BadRequestException.class,
                () -> paymentService.verify(user.getEmail(), first.order().orderNumber(),
                        otherGatewayOrderId, "pay_123", genuineSignature(otherGatewayOrderId, "pay_123")));

        assertEquals("PENDING_PAYMENT", data.statusOf(first.order().orderNumber()));
    }

    @Test
    void verify_cannotBeUsedOnSomeoneElsesOrder() {

        CheckoutResponse checkout = checkout();
        String gatewayOrderId = checkout.payment().gatewayOrderId();
        User stranger = data.customer();

        assertThrows(NotFoundException.class,
                () -> paymentService.verify(stranger.getEmail(), checkout.order().orderNumber(),
                        gatewayOrderId, "pay_123", genuineSignature(gatewayOrderId, "pay_123")));
    }

    @Test
    void verify_twice_isHarmless() {

        CheckoutResponse checkout = checkout();
        String gatewayOrderId = checkout.payment().gatewayOrderId();
        String signature = genuineSignature(gatewayOrderId, "pay_123");

        assertTrue(paymentService.verify(user.getEmail(), checkout.order().orderNumber(),
                gatewayOrderId, "pay_123", signature));
        assertTrue(paymentService.verify(user.getEmail(), checkout.order().orderNumber(),
                gatewayOrderId, "pay_123", signature));

        assertEquals("PLACED", data.statusOf(checkout.order().orderNumber()));
        assertEquals(8, data.stockOf(bottle));
    }

    @Test
    void demoConfirm_paysTheOrderThroughTheSameVerification() {

        CheckoutResponse checkout = checkout();

        assertTrue(paymentService.demoConfirm(user.getEmail(), checkout.order().orderNumber()));

        assertEquals("PLACED", data.statusOf(checkout.order().orderNumber()));
        assertEquals(PaymentStatus.PAID, paymentOf(checkout).getStatus());
    }

    // ---------- gateway notification (webhook) ----------

    @Test
    void webhook_withAValidSignature_marksTheOrderPaid() {

        CheckoutResponse checkout = checkout();
        String body = webhookBody("payment.captured", checkout.payment().gatewayOrderId(), "pay_777", 150_000);

        paymentService.handleWebhook(body, Signatures.hmacSha256Hex(body, WEBHOOK_SECRET));

        assertEquals("PLACED", data.statusOf(checkout.order().orderNumber()));
        assertEquals("pay_777", paymentOf(checkout).getGatewayPaymentId());
    }

    @Test
    void webhook_withAWrongOrMissingSignature_isRefused() {

        CheckoutResponse checkout = checkout();
        String body = webhookBody("payment.captured", checkout.payment().gatewayOrderId(), "pay_777", 150_000);

        assertThrows(BadRequestException.class,
                () -> paymentService.handleWebhook(body, Signatures.hmacSha256Hex(body, "wrong-secret")));
        assertThrows(BadRequestException.class, () -> paymentService.handleWebhook(body, null));

        assertEquals("PENDING_PAYMENT", data.statusOf(checkout.order().orderNumber()));
    }

    @Test
    void webhook_isRefusedIfTheBodyWasChangedAfterSigning() {

        CheckoutResponse checkout = checkout();
        String original = webhookBody("payment.captured", "order_someone_else", "pay_777", 150_000);
        String signature = Signatures.hmacSha256Hex(original, WEBHOOK_SECRET);

        String tampered = original.replace("order_someone_else", checkout.payment().gatewayOrderId());

        assertThrows(BadRequestException.class, () -> paymentService.handleWebhook(tampered, signature));
        assertEquals("PENDING_PAYMENT", data.statusOf(checkout.order().orderNumber()));
    }

    @Test
    void webhook_withTheWrongAmount_doesNotPayTheOrder() {

        CheckoutResponse checkout = checkout();
        String body = webhookBody("payment.captured", checkout.payment().gatewayOrderId(), "pay_777", 100);

        paymentService.handleWebhook(body, Signatures.hmacSha256Hex(body, WEBHOOK_SECRET));

        assertEquals("PENDING_PAYMENT", data.statusOf(checkout.order().orderNumber()));
    }

    @Test
    void webhookAndBrowserBothReportingTheSamePayment_confirmItOnce() {

        CheckoutResponse checkout = checkout();
        String gatewayOrderId = checkout.payment().gatewayOrderId();
        String body = webhookBody("payment.captured", gatewayOrderId, "pay_777", 150_000);
        String webhookSignature = Signatures.hmacSha256Hex(body, WEBHOOK_SECRET);

        paymentService.handleWebhook(body, webhookSignature);
        paymentService.handleWebhook(body, webhookSignature);   // gateways do resend
        assertTrue(paymentService.verify(user.getEmail(), checkout.order().orderNumber(),
                gatewayOrderId, "pay_777", genuineSignature(gatewayOrderId, "pay_777")));

        assertEquals("PLACED", data.statusOf(checkout.order().orderNumber()));
        assertEquals(PaymentStatus.PAID, paymentOf(checkout).getStatus());
        assertEquals(8, data.stockOf(bottle));
    }

    // ---------- orders that are never paid ----------

    @Test
    void unpaidOrder_isCancelledAfterThePaymentWindow_andStockComesBack() {

        CheckoutResponse checkout = checkout();
        assertEquals(8, data.stockOf(bottle));

        // "Now plus a minute" stands in for the payment window having passed
        int cancelled = paymentService.expireOlderThan(LocalDateTime.now().plusMinutes(1));

        assertEquals(1, cancelled);
        assertEquals("CANCELLED", data.statusOf(checkout.order().orderNumber()));
        assertEquals(PaymentStatus.EXPIRED, paymentOf(checkout).getStatus());
        assertEquals(10, data.stockOf(bottle));
    }

    @Test
    void recentUnpaidOrders_andPaidOrders_areLeftAlone() {

        CheckoutResponse paid = checkout();
        paymentService.demoConfirm(user.getEmail(), paid.order().orderNumber());

        User other = data.customer();
        data.addToCart(other, bottle, 1);
        CheckoutResponse recent = paymentService.checkout(other.getEmail(), ADDRESS);

        // Nothing is older than 15 minutes
        assertEquals(0, paymentService.expireOlderThan(LocalDateTime.now().minusMinutes(15)));
        assertEquals("PENDING_PAYMENT", data.statusOf(recent.order().orderNumber()));

        // Even when the window has passed, a paid order is never cancelled
        assertEquals(1, paymentService.expireOlderThan(LocalDateTime.now().plusMinutes(1)));
        assertEquals("PLACED", data.statusOf(paid.order().orderNumber()));
        assertEquals(8, data.stockOf(bottle));
    }

    @Test
    void paymentArrivingAfterTheOrderExpired_isFlaggedForRefund_andStockIsNotTakenAgain() {

        CheckoutResponse checkout = checkout();
        String gatewayOrderId = checkout.payment().gatewayOrderId();
        paymentService.expireOlderThan(LocalDateTime.now().plusMinutes(1));

        assertThrows(BadRequestException.class,
                () -> paymentService.verify(user.getEmail(), checkout.order().orderNumber(),
                        gatewayOrderId, "pay_late", genuineSignature(gatewayOrderId, "pay_late")));

        assertEquals("CANCELLED", data.statusOf(checkout.order().orderNumber()));
        assertEquals(10, data.stockOf(bottle));
        assertEquals(PaymentStatus.NEEDS_REFUND, paymentOf(checkout).getStatus());
        assertEquals("pay_late", paymentOf(checkout).getGatewayPaymentId());
    }
}
