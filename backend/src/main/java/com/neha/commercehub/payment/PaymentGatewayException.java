package com.neha.commercehub.payment;

/** The payment company could not be reached or refused the request. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }
}
