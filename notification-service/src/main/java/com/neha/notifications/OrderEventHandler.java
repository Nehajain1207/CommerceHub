package com.neha.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Turns an order event into a notification for the customer.
 *
 * Kafka promises to deliver every message AT LEAST once, which means the same
 * message can arrive twice (after a restart, a retry, a network hiccup). So this
 * handler is written to be safe to repeat: an event that was already handled is
 * recognised by its eventId and skipped.
 *
 * (A real system would send an email or SMS here. This one records the
 * notification, which the shop shows as the order's timeline.)
 */
@Service
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

    private static final Map<String, String> MESSAGES = Map.of(
            "ORDER_CREATED", "Order placed. Complete the payment within 15 minutes to confirm it.",
            "ORDER_PAID", "Payment received. Your order is confirmed.",
            "ORDER_SHIPPED", "Your order has shipped.",
            "ORDER_DELIVERED", "Your order was delivered.",
            "ORDER_CANCELLED", "Your order was cancelled.");

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public OrderEventHandler(NotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    /** Returns true if a new notification was recorded, false if the event was skipped. */
    public boolean handle(String payload) {

        OrderEventMessage event;

        try {
            event = objectMapper.readValue(payload, OrderEventMessage.class);
        } catch (Exception ex) {
            // A message that cannot be read will never become readable: skip it instead of retrying for ever
            log.warn("Skipping an event that could not be read: {}", ex.getMessage());
            return false;
        }

        if (event == null || event.eventId() == null || event.orderNumber() == null || event.customerEmail() == null) {
            log.warn("Skipping an event with missing fields.");
            return false;
        }

        String message = MESSAGES.get(event.type());

        if (message == null) {
            // A newer kind of event this service does not know about yet
            return false;
        }

        if (notificationRepository.existsByEventId(event.eventId())) {
            return false;   // already handled
        }

        try {

            notificationRepository.save(new Notification(
                    event.eventId(),
                    event.orderNumber(),
                    event.customerEmail(),
                    event.type(),
                    message,
                    event.occurredAt() == null ? LocalDateTime.now() : event.occurredAt()));

            return true;

        } catch (DataIntegrityViolationException ex) {

            // Two copies of the same event were handled at the same moment; the database kept one
            return false;
        }
    }
}
