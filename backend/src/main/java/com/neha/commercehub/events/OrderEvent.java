package com.neha.commercehub.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The message other services receive when something happens to an order.
 * eventId is unique per event, so a receiver that gets the same message twice can tell.
 */
public record OrderEvent(
        String eventId,
        String type,
        String orderNumber,
        String customerName,
        String customerEmail,
        BigDecimal total,
        LocalDateTime occurredAt) {
}
