package com.neha.commercehub.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the publisher every second. Only exists when Kafka is switched on. */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OutboxSchedule {

    private final OutboxPublisher publisher;

    public OutboxSchedule(OutboxRepository outboxRepository, EventSender sender) {
        this.publisher = new OutboxPublisher(outboxRepository, sender);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void run() {
        publisher.publishPending();
    }
}
