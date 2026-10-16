package com.neha.commercehub.payment;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for a payment company when no Razorpay keys are configured, so the
 * project still runs for anyone who downloads it. No money and no network involved.
 *
 * For chaos testing it can be told to fail a share of calls (app.payment.demo-failure-rate,
 * for example 0.6 = 60%), to show the retry and circuit breaker working.
 */
public class DemoGateway implements PaymentGateway {

    static final String DEMO_SECRET = "demo-secret";

    private final double failureRate;

    public DemoGateway() {
        this(0);
    }

    public DemoGateway(double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public String createOrder(String receipt, long amountPaise) {

        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new TransientGatewayException("Simulated payment provider failure (chaos test setting).");
        }

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
