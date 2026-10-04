package com.neha.notifications;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against an in-memory database; nothing connects to Kafka. */
@SpringBootTest
class OrderEventHandlerTest {

    private static final String SECRET = "test-only-secret-0123456789-0123456789-abcdef";

    @Autowired
    private OrderEventHandler handler;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationController controller;

    @BeforeEach
    void cleanDatabase() {
        notificationRepository.deleteAll();
    }

    private static String event(String eventId, String type, String orderNumber, String email) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"orderNumber\":\"" + orderNumber + "\","
                + "\"customerName\":\"Test\",\"customerEmail\":\"" + email + "\",\"total\":1500.00,"
                + "\"occurredAt\":\"2026-10-04T10:15:30\"}";
    }

    private static String token(String email, String role) {
        return "Bearer " + Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    // ---------- handling events ----------

    @Test
    void anEvent_becomesANotificationForTheCustomer() {

        assertTrue(handler.handle(event("e1", "ORDER_PAID", "CH-1", "neha@example.com")));

        List<Notification> saved = notificationRepository.findAll();
        assertEquals(1, saved.size());
        assertEquals("CH-1", saved.get(0).getOrderNumber());
        assertEquals("neha@example.com", saved.get(0).getCustomerEmail());
        assertEquals("Payment received. Your order is confirmed.", saved.get(0).getMessage());
    }

    @Test
    void theSameEventDeliveredTwice_isHandledOnce() {

        String paid = event("e1", "ORDER_PAID", "CH-1", "neha@example.com");

        assertTrue(handler.handle(paid));
        assertFalse(handler.handle(paid));
        assertFalse(handler.handle(paid));

        assertEquals(1, notificationRepository.count());
    }

    @Test
    void differentEventsForOneOrder_eachBecomeANotification() {

        handler.handle(event("e1", "ORDER_CREATED", "CH-1", "neha@example.com"));
        handler.handle(event("e2", "ORDER_PAID", "CH-1", "neha@example.com"));
        handler.handle(event("e3", "ORDER_SHIPPED", "CH-1", "neha@example.com"));

        assertEquals(3, notificationRepository.count());
    }

    @Test
    void unreadableOrUnknownEvents_areSkippedWithoutCrashing() {

        assertFalse(handler.handle("this is not json"));
        assertFalse(handler.handle("{}"));
        assertFalse(handler.handle(event("e9", "SOMETHING_NEW", "CH-1", "neha@example.com")));

        assertEquals(0, notificationRepository.count());
    }

    @Test
    void anEventWithExtraFields_isStillRead() {

        String withExtra = event("e1", "ORDER_PAID", "CH-1", "neha@example.com")
                .replace("{\"eventId\"", "{\"addedLater\":\"ignored\",\"eventId\"");

        assertTrue(handler.handle(withExtra));
    }

    // ---------- who may read what ----------

    @Test
    void aCustomerSeesOnlyTheirOwnUpdates() {

        handler.handle(event("e1", "ORDER_PAID", "CH-1", "neha@example.com"));
        handler.handle(event("e2", "ORDER_PAID", "CH-2", "someone.else@example.com"));

        var mine = controller.mine(token("neha@example.com", "CUSTOMER"));

        assertEquals(1, mine.size());
        assertEquals("CH-1", mine.get(0).orderNumber());
    }

    @Test
    void aMissingOrForgedToken_isRefused() {

        assertThrows(ResponseStatusException.class, () -> controller.mine(null));
        assertThrows(ResponseStatusException.class, () -> controller.mine("Bearer not-a-real-token"));
        assertThrows(ResponseStatusException.class,
                () -> controller.mine(token("neha@example.com", "CUSTOMER") + "x"));
    }

    @Test
    void onlyAdminsSeeEveryonesActivity() {

        handler.handle(event("e1", "ORDER_PAID", "CH-1", "neha@example.com"));
        handler.handle(event("e2", "ORDER_PAID", "CH-2", "someone.else@example.com"));

        assertEquals(2, controller.all(token("admin@commercehub.local", "ADMIN")).size());
        assertThrows(ResponseStatusException.class, () -> controller.all(token("neha@example.com", "CUSTOMER")));
    }
}
