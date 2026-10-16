package com.neha.commercehub.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

/**
 * Chooses the real gateway when Razorpay keys are present, and the demo one otherwise,
 * and wraps either one with retry + circuit breaker (see ResilientPaymentGateway).
 */
@Configuration
@EnableScheduling
public class PaymentConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfig.class);

    @Bean
    public PaymentGateway paymentGateway(@Value("${razorpay.key-id}") String keyId,
                                         @Value("${razorpay.key-secret}") String keySecret,
                                         @Value("${app.payment.demo-failure-rate:0}") double demoFailureRate,
                                         ObjectMapper objectMapper,
                                         ObjectProvider<MeterRegistry> meterRegistry) {

        PaymentGateway gateway;

        if (keyId.isBlank() || keySecret.isBlank()) {
            log.info("No Razorpay keys configured: payments run in DEMO mode.");
            gateway = new DemoGateway(demoFailureRate);
        } else {
            log.info("Razorpay keys found: payments go through Razorpay.");
            gateway = new RazorpayGateway(keyId.trim(), keySecret.trim(), objectMapper);
        }

        return withResilience(gateway, 200, meterRegistry.getIfAvailable());
    }

    /**
     * Wraps a gateway with retry and a circuit breaker.
     * firstRetryWaitMillis is 200 in the app; tests pass 1 so they run quickly.
     */
    static ResilientPaymentGateway withResilience(PaymentGateway gateway,
                                                  long firstRetryWaitMillis,
                                                  MeterRegistry meters) {

        CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)                       // judge by the last 20 calls
                .minimumNumberOfCalls(10)                    // ...once there have been at least 10
                .failureRateThreshold(50)                    // open when half of them failed
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(PaymentGatewayException.class)
                .build());

        RetryRegistry retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(firstRetryWaitMillis, 2))
                .retryExceptions(TransientGatewayException.class)
                .build());

        // Circuit state, failure rate and retry counts appear in Prometheus / Grafana
        if (meters != null) {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakers).bindTo(meters);
            TaggedRetryMetrics.ofRetryRegistry(retries).bindTo(meters);
        }

        return new ResilientPaymentGateway(
                gateway,
                circuitBreakers.circuitBreaker("payment-gateway"),
                retries.retry("payment-gateway"));
    }
}
