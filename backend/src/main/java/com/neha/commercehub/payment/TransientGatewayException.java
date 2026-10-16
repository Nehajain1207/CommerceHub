package com.neha.commercehub.payment;

/**
 * A payment-gateway failure that is worth trying again: a timeout, a dropped connection,
 * or a 5xx / 429 answer. A 4xx answer (our request was wrong) is NOT transient.
 */
public class TransientGatewayException extends PaymentGatewayException {

    public TransientGatewayException(String message) {
        super(message);
    }
}
