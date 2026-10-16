package com.neha.commercehub.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Exposes "events waiting in the outbox" as a metric. Normally close to 0; it grows while
 * Kafka is down and falls back to 0 once Kafka returns, which shows on the Grafana dashboard.
 */
@Component
public class OutboxMetrics {

    public OutboxMetrics(ObjectProvider<MeterRegistry> registry, OutboxRepository outboxRepository) {

        registry.ifAvailable(meters ->
                Gauge.builder("commercehub.outbox.pending", outboxRepository, OutboxRepository::countByPublishedAtIsNull)
                        .description("Order events saved in the outbox but not yet sent to Kafka")
                        .register(meters));
    }
}
