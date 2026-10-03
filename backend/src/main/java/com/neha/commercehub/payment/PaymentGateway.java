package com.neha.commercehub.payment;

/**
 * The outside payment company, seen from our code. There are two versions:
 * RazorpayGateway (real, test mode) and DemoGateway (no account needed).
 */
public interface PaymentGateway {

    /** Asks the gateway to open an order for this amount and returns the gateway's id for it. */
    String createOrder(String receipt, long amountPaise);

    /** The public key the browser needs to open the payment window. Empty in demo mode. */
    String publicKey();

    /** The secret used to check that a "payment succeeded" message really came from the gateway. */
    String signingSecret();

    boolean isDemo();
}
