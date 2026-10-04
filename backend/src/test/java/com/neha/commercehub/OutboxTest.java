package com.neha.commercehub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.common.InsufficientStockException;
import com.neha.commercehub.events.EventSender;
import com.neha.commercehub.events.OutboxEvent;
import com.neha.commercehub.events.OutboxPublisher;
import com.neha.commercehub.events.OutboxRepository;
import com.neha.commercehub.order.OrderService;
import com.neha.commercehub.order.OrderStatus;
import com.neha.commercehub.payment.PaymentDtos.CheckoutResponse;
import com.neha.commercehub.payment.PaymentService;
import com.neha.commercehub.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Order events and the outbox. Kafka itself is switched off in tests; a fake
 * sender stands in for it so "Kafka is down" can be simulated on demand.
 */
@SpringBootTest
class OutboxTest {

    private static final String ADDRESS = "12 MG Road, Bengaluru";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestData data;

    private User user;
    private Product bottle;

    /** Records what was "sent", and can be told to fail like an unreachable Kafka. */
    static class FakeSender implements EventSender {

        final List<String> sentPayloads = new ArrayList<>();
        final List<String> sentKeys = new ArrayList<>();
        boolean down;

        @Override
        public void send(String key, String payload) throws Exception {
            if (down) {
                throw new IllegalStateException("Kafka is not reachable");
            }
            sentKeys.add(key);
            sentPayloads.add(payload);
        }
    }

    @BeforeEach
    void setUp() {
        data.clear();
        user = data.customer();
        bottle = data.product("Bottle", "750.00", 10);
        data.addToCart(user, bottle, 2);
    }

    private List<String> eventTypes(String orderNumber) {
        return outboxRepository.findByOrderNumberOrderByIdAsc(orderNumber).stream()
                .map(OutboxEvent::getEventType)
                .toList();
    }

    // ---------- events are written with the order change ----------

    @Test
    void checkout_writesAnOrderCreatedEvent_withTheOrderDetails() throws Exception {

        CheckoutResponse checkout = paymentService.checkout(user.getEmail(), ADDRESS);
        String orderNumber = checkout.order().orderNumber();

        List<OutboxEvent> events = outboxRepository.findByOrderNumberOrderByIdAsc(orderNumber);
        assertEquals(1, events.size());
        assertEquals("ORDER_CREATED", events.get(0).getEventType());
        assertNull(events.get(0).getPublishedAt());

        JsonNode payload = objectMapper.readTree(events.get(0).getPayload());
        assertEquals(orderNumber, payload.get("orderNumber").asText());
        assertEquals(user.getEmail(), payload.get("customerEmail").asText());
        assertEquals("ORDER_CREATED", payload.get("type").asText());
        assertEquals(events.get(0).getEventId(), payload.get("eventId").asText());
    }

    @Test
    void failedCheckout_writesNoEvent() {

        Product scarce = data.product("Scarce", "100.00", 1);
        data.addToCart(user, scarce, 5);   // more than there is

        assertThrows(InsufficientStockException.class, () -> paymentService.checkout(user.getEmail(), ADDRESS));

        // The order rolled back, and the event rolled back with it
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void paying_writesOrderPaid_once_evenIfConfirmedTwice() {

        CheckoutResponse checkout = paymentService.checkout(user.getEmail(), ADDRESS);
        String orderNumber = checkout.order().orderNumber();

        paymentService.demoConfirm(user.getEmail(), orderNumber);
        paymentService.demoConfirm(user.getEmail(), orderNumber);

        assertEquals(List.of("ORDER_CREATED", "ORDER_PAID"), eventTypes(orderNumber));
    }

    @Test
    void fullOrderLife_writesEventsInOrder() {

        CheckoutResponse checkout = paymentService.checkout(user.getEmail(), ADDRESS);
        String orderNumber = checkout.order().orderNumber();

        paymentService.demoConfirm(user.getEmail(), orderNumber);
        orderService.changeStatus(orderNumber, OrderStatus.SHIPPED);
        orderService.changeStatus(orderNumber, OrderStatus.DELIVERED);

        assertEquals(List.of("ORDER_CREATED", "ORDER_PAID", "ORDER_SHIPPED", "ORDER_DELIVERED"),
                eventTypes(orderNumber));
    }

    @Test
    void cancelling_andExpiry_writeOrderCancelled_once() {

        CheckoutResponse cancelled = paymentService.checkout(user.getEmail(), ADDRESS);
        orderService.cancelMyOrder(user.getEmail(), cancelled.order().orderNumber());
        assertThrows(RuntimeException.class,
                () -> orderService.cancelMyOrder(user.getEmail(), cancelled.order().orderNumber()));

        User other = data.customer();
        data.addToCart(other, bottle, 1);
        CheckoutResponse expired = paymentService.checkout(other.getEmail(), ADDRESS);
        paymentService.expireOlderThan(LocalDateTime.now().plusMinutes(1));

        assertEquals(List.of("ORDER_CREATED", "ORDER_CANCELLED"), eventTypes(cancelled.order().orderNumber()));
        assertEquals(List.of("ORDER_CREATED", "ORDER_CANCELLED"), eventTypes(expired.order().orderNumber()));
    }

    // ---------- sending ----------

    @Test
    void publisher_sendsWaitingEventsInOrder_andMarksThemSent() {

        CheckoutResponse checkout = paymentService.checkout(user.getEmail(), ADDRESS);
        String orderNumber = checkout.order().orderNumber();
        paymentService.demoConfirm(user.getEmail(), orderNumber);

        FakeSender sender = new FakeSender();
        OutboxPublisher publisher = new OutboxPublisher(outboxRepository, sender);

        assertEquals(2, publisher.publishPending());

        assertEquals(List.of(orderNumber, orderNumber), sender.sentKeys);
        assertTrue(sender.sentPayloads.get(0).contains("ORDER_CREATED"));
        assertTrue(sender.sentPayloads.get(1).contains("ORDER_PAID"));
        assertEquals(0, outboxRepository.countByPublishedAtIsNull());
        outboxRepository.findAll().forEach(event -> assertNotNull(event.getPublishedAt()));

        // Nothing is sent a second time
        assertEquals(0, publisher.publishPending());
        assertEquals(2, sender.sentPayloads.size());
    }

    @Test
    void whenKafkaIsDown_eventsAreKept_andSentOnceItIsBack() {

        CheckoutResponse checkout = paymentService.checkout(user.getEmail(), ADDRESS);
        paymentService.demoConfirm(user.getEmail(), checkout.order().orderNumber());

        FakeSender sender = new FakeSender();
        sender.down = true;
        OutboxPublisher publisher = new OutboxPublisher(outboxRepository, sender);

        assertEquals(0, publisher.publishPending());
        assertEquals(0, publisher.publishPending());
        assertEquals(2, outboxRepository.countByPublishedAtIsNull());   // nothing lost

        sender.down = false;

        assertEquals(2, publisher.publishPending());
        assertEquals(0, outboxRepository.countByPublishedAtIsNull());
        assertTrue(sender.sentPayloads.get(0).contains("ORDER_CREATED"));   // still in order
        assertTrue(sender.sentPayloads.get(1).contains("ORDER_PAID"));
    }
}
