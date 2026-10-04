package com.neha.commercehub.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * An event waiting to be sent to Kafka.
 *
 * Why a table and not "just send it to Kafka"? Saving the order and sending the
 * message are two different systems. If the app saved the order and then crashed
 * before sending, the event would be lost for ever. So the event is written to
 * this table IN THE SAME DATABASE TRANSACTION as the order change: either both
 * are saved or neither is. A background job then sends the rows to Kafka and
 * marks them sent. This is called the outbox pattern.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String eventType;

    /** Used as the Kafka message key, so all events of one order stay in order. */
    @Column(nullable = false)
    private String orderNumber;

    @Column(nullable = false, length = 2000)
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Empty until Kafka has confirmed it received the message. */
    private LocalDateTime publishedAt;

    public OutboxEvent() {
    }

    public OutboxEvent(String eventId, String eventType, String orderNumber, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.orderNumber = orderNumber;
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
}
