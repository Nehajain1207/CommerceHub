package com.neha.commercehub.payment;

import java.util.UUID;

/**
 * Stands in for a payment company when no Razorpay keys are configured, so the
 * project still runs for anyone who downloads it. No money and no network involved.
 */
public class DemoGateway implements PaymentGateway {

    static final String DEMO_SECRET = "demo-secret";

    @Override
    public String createOrder(String receipt, long amountPaise) {
        return "demo_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    @Override
    public String publicKey() {
        return "";
    }

    @Override
    public String signingSecret() {
        return DEMO_SECRET;
    }

    @Override
    public boolean isDemo() {
        return true;
    }
}
