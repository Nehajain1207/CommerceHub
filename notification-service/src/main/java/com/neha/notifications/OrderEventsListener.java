package com.neha.notifications;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** The only way this service learns about orders: messages on the Kafka topic. */
@Component
public class OrderEventsListener {

    private final OrderEventHandler handler;

    public OrderEventsListener(OrderEventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = "${app.kafka.order-events-topic}")
    public void onMessage(String payload) {
        handler.handle(payload);
    }
}
