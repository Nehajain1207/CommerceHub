package com.neha.commercehub.payment;

import com.neha.commercehub.order.OrderDtos.CheckoutRequest;
import com.neha.commercehub.payment.PaymentDtos.CheckoutResponse;
import com.neha.commercehub.payment.PaymentDtos.PaymentInfo;
import com.neha.commercehub.payment.PaymentDtos.VerifyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Turns the cart into an order that is waiting for payment. */
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request, Authentication authentication) {
        return paymentService.checkout(authentication.getName(), request.shippingAddress());
    }

    @GetMapping("/orders/{orderNumber}/payment")
    public PaymentInfo paymentInfo(@PathVariable String orderNumber, Authentication authentication) {
        return paymentService.paymentInfo(authentication.getName(), orderNumber);
    }

    @PostMapping("/orders/{orderNumber}/payment/verify")
    public Map<String, Boolean> verify(@PathVariable String orderNumber,
                                       @Valid @RequestBody VerifyRequest request,
                                       Authentication authentication) {

        boolean paid = paymentService.verify(
                authentication.getName(),
                orderNumber,
                request.gatewayOrderId(),
                request.gatewayPaymentId(),
                request.signature());

        return Map.of("paid", paid);
    }

    /** Only works when no real gateway is configured. */
    @PostMapping("/orders/{orderNumber}/payment/demo-confirm")
    public Map<String, Boolean> demoConfirm(@PathVariable String orderNumber, Authentication authentication) {
        return Map.of("paid", paymentService.demoConfirm(authentication.getName(), orderNumber));
    }

    /**
     * Called by the gateway's servers, not by a logged-in user, so it is open in
     * SecurityConfig and protected by the signature check instead.
     */
    @PostMapping("/payments/webhook")
    public Map<String, String> webhook(@RequestBody String rawBody,
                                       @RequestHeader(value = "X-Razorpay-Signature", required = false)
                                       String signature) {

        paymentService.handleWebhook(rawBody, signature);
        return Map.of("status", "ok");
    }
}
