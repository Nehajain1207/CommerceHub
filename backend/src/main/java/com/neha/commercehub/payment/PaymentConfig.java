package com.neha.commercehub.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Chooses the real gateway when Razorpay keys are present, and the demo one otherwise. */
@Configuration
@EnableScheduling
public class PaymentConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfig.class);

    @Bean
    public PaymentGateway paymentGateway(@Value("${razorpay.key-id}") String keyId,
                                         @Value("${razorpay.key-secret}") String keySecret,
                                         ObjectMapper objectMapper) {

        if (keyId.isBlank() || keySecret.isBlank()) {
            log.info("No Razorpay keys configured: payments run in DEMO mode.");
            return new DemoGateway();
        }

        log.info("Razorpay keys found: payments go through Razorpay.");
        return new RazorpayGateway(keyId.trim(), keySecret.trim(), objectMapper);
    }
}
