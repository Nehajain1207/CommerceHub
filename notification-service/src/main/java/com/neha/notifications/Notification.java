package com.neha.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One update for a customer about one of their orders. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The id of the event this came from. The UNIQUE rule is what makes handling
     * safe to repeat: if Kafka delivers the same event twice, the second insert is refused.
     */
    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String orderNumber;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public Notification() {
    }

    public Notification(String eventId, String orderNumber, String customerEmail,
                        String type, String message, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.orderNumber = orderNumber;
        this.customerEmail = customerEmail;
        this.type = type;
        this.message = message;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
