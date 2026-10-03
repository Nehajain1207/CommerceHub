package com.neha.commercehub.payment;

import com.neha.commercehub.order.OrderDtos.OrderResponse;
import jakarta.validation.constraints.NotBlank;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** What the browser needs to open the payment window. */
    public record PaymentInfo(
            String gatewayOrderId,
            long amountPaise,
            String currency,
            String publicKey,
            boolean demo) {
    }

    public record CheckoutResponse(OrderResponse order, PaymentInfo payment) {
    }

    /** What the payment window hands back to the browser after a successful payment. */
    public record VerifyRequest(
            @NotBlank String gatewayOrderId,
            @NotBlank String gatewayPaymentId,
            @NotBlank String signature) {
    }
}
