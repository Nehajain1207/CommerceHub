package com.neha.commercehub.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaEventSender implements EventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaEventSender(KafkaTemplate<String, String> kafkaTemplate,
                            @Value("${app.kafka.order-events-topic}") String topic) {

        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /** Waits for Kafka's acknowledgement, so "sent" really means "Kafka has it". */
    @Override
    public void send(String key, String payload) throws Exception {
        kafkaTemplate.send(topic, key, payload).get(10, TimeUnit.SECONDS);
    }
}
