package com.neha.commercehub.payment;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Retry and circuit breaker around the payment gateway. Plain unit tests, no Spring. */
class ResilientPaymentGatewayTest {

    /** A gateway whose createOrder does whatever the test says. */
    private record StubGateway(Supplier<String> behaviour) implements PaymentGateway {

        @Override
        public String createOrder(String receipt, long amountPaise) {
            return behaviour.get();
        }

        @Override
        public String publicKey() {
            return "";
        }

        @Override
        public String signingSecret() {
            return "secret";
        }

        @Override
        public boolean isDemo() {
            return true;
        }
    }

    @Test
    void transientFailuresAreRetriedUntilTheCallWorks() {

        AtomicInteger calls = new AtomicInteger();
        PaymentGateway flaky = new StubGateway(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new TransientGatewayException("network blip");
            }
            return "order_123";
        });

        PaymentGateway gateway = PaymentConfig.withResilience(flaky, 1, null);

        assertThat(gateway.createOrder("receipt", 100)).isEqualTo("order_123");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void permanentErrorsAreNotRetried() {

        AtomicInteger calls = new AtomicInteger();
        PaymentGateway rejecting = new StubGateway(() -> {
            calls.incrementAndGet();
            throw new PaymentGatewayException("Razorpay answered 400");
        });

        PaymentGateway gateway = PaymentConfig.withResilience(rejecting, 1, null);

        assertThatThrownBy(() -> gateway.createOrder("receipt", 100))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("400");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void circuitOpensWhenTheProviderKeepsFailing() {

        AtomicInteger calls = new AtomicInteger();
        PaymentGateway down = new StubGateway(() -> {
            calls.incrementAndGet();
            throw new TransientGatewayException("provider down");
        });

        PaymentGateway gateway = PaymentConfig.withResilience(down, 1, null);

        // Enough failing calls for the circuit breaker to judge the provider as down
        for (int i = 0; i < 6; i++) {
            try {
                gateway.createOrder("receipt-" + i, 100);
            } catch (PaymentGatewayException expected) {
                // every one of these fails
            }
        }

        int callsBefore = calls.get();

        // Now customers are failed fast: the struggling provider is not called at all
        assertThatThrownBy(() -> gateway.createOrder("receipt-next", 100))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("paused");
        assertThat(calls.get()).isEqualTo(callsBefore);
    }

    @Test
    void demoGatewayCanBeToldToFailForChaosTests() {

        assertThatThrownBy(() -> new DemoGateway(1.0).createOrder("receipt", 100))
                .isInstanceOf(TransientGatewayException.class);

        assertThat(new DemoGateway(0).createOrder("receipt", 100)).startsWith("demo_order_");
    }
}
