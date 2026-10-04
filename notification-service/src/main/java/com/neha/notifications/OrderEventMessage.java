package com.neha.notifications;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** The shape of an order event as it arrives from Kafka. Mirrors OrderEvent in the shop back end. */
public record OrderEventMessage(
        String eventId,
        String type,
        String orderNumber,
        String customerName,
        String customerEmail,
        BigDecimal total,
        LocalDateTime occurredAt) {
}
