package com.neha.commercehub.payment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import java.util.function.Supplier;

/**
 * Wraps the real gateway with two protections (Resilience4j):
 *
 *  - Retry: a transient failure (timeout, 5xx) is tried again up to 3 times, waiting a
 *    little longer each time (200 ms, then 400 ms).
 *  - Circuit breaker: if most recent calls are failing, the provider is clearly down. The
 *    circuit "opens" and for 30 seconds we fail immediately instead of making every customer
 *    wait for a timeout and piling more load on a struggling provider. Then a few test calls
 *    are let through; if they work, normal service resumes.
 *
 * Creating a gateway order does not charge anyone, so retrying it is safe.
 */
public class ResilientPaymentGateway implements PaymentGateway {

    private final PaymentGateway delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientPaymentGateway(PaymentGateway delegate, CircuitBreaker circuitBreaker, Retry retry) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Override
    public String createOrder(String receipt, long amountPaise) {

        Supplier<String> call = () -> delegate.createOrder(receipt, amountPaise);
        Supplier<String> protectedCall = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call));

        try {
            return protectedCall.get();
        } catch (CallNotPermittedException ex) {
            throw new PaymentGatewayException(
                    "Payments are paused for a moment because the payment provider is failing.");
        }
    }

    @Override
    public String publicKey() {
        return delegate.publicKey();
    }

    @Override
    public String signingSecret() {
        return delegate.signingSecret();
    }

    @Override
    public boolean isDemo() {
        return delegate.isDemo();
    }
}
