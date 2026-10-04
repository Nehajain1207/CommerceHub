package com.neha.commercehub.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neha.commercehub.order.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Writes an event row next to an order change, inside the caller's transaction. */
@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * MANDATORY means this refuses to run unless a transaction is already open.
     * That is the whole point: the event must be saved together with the order
     * change, never on its own.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(OrderEventType type, Order order) {

        OrderEvent event = new OrderEvent(
                UUID.randomUUID().toString(),
                type.name(),
                order.getOrderNumber(),
                order.getUser().getName(),
                order.getUser().getEmail(),
                order.getTotal(),
                LocalDateTime.now());

        try {

            outboxRepository.save(new OutboxEvent(
                    event.eventId(),
                    event.type(),
                    event.orderNumber(),
                    objectMapper.writeValueAsString(event)));

        } catch (JsonProcessingException ex) {

            throw new IllegalStateException("Could not write event for order " + order.getOrderNumber(), ex);
        }
    }
}
