package com.neha.commercehub;

import com.neha.commercehub.cart.CartItemRepository;
import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.common.BadRequestException;
import com.neha.commercehub.common.InsufficientStockException;
import com.neha.commercehub.common.NotFoundException;
import com.neha.commercehub.order.OrderDtos.OrderResponse;
import com.neha.commercehub.order.OrderRepository;
import com.neha.commercehub.order.OrderService;
import com.neha.commercehub.order.OrderStatus;
import com.neha.commercehub.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checkout, cancellation and order status rules, run against a real (in-memory) database. */
@SpringBootTest
class OrderServiceTest {

    private static final String ADDRESS = "12 MG Road, Bengaluru";

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private TestData data;

    @BeforeEach
    void cleanDatabase() {
        data.clear();
    }

    @Test
    void checkout_createsOrder_takesStock_andEmptiesCart() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);
        Product kettle = data.product("Kettle", "1100.00", 5);
        data.addToCart(user, bottle, 2);
        data.addToCart(user, kettle, 1);

        OrderResponse order = orderService.checkout(user.getEmail(), ADDRESS);

        assertEquals("PENDING_PAYMENT", order.status());
        assertEquals(2, order.items().size());
        assertEquals(0, new BigDecimal("2600.00").compareTo(order.total()));
        assertTrue(order.orderNumber().startsWith("CH-"));

        assertEquals(8, data.stockOf(bottle));
        assertEquals(4, data.stockOf(kettle));
        assertTrue(cartItemRepository.findByUserIdOrderByIdAsc(user.getId()).isEmpty());
    }

    @Test
    void checkout_failsWhenCartIsEmpty() {

        User user = data.customer();

        assertThrows(BadRequestException.class, () -> orderService.checkout(user.getEmail(), ADDRESS));
    }

    @Test
    void checkout_failsWhenNotEnoughStock_andChangesNothing() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);
        Product kettle = data.product("Kettle", "1100.00", 1);
        data.addToCart(user, bottle, 3);
        data.addToCart(user, kettle, 2);   // only 1 left

        assertThrows(InsufficientStockException.class, () -> orderService.checkout(user.getEmail(), ADDRESS));

        // The bottle stock taken earlier in the same order must be put back
        assertEquals(10, data.stockOf(bottle));
        assertEquals(1, data.stockOf(kettle));
        assertEquals(0, orderRepository.count());
        assertEquals(2, cartItemRepository.findByUserIdOrderByIdAsc(user.getId()).size());
    }

    @Test
    void checkout_canTakeTheLastUnit_butNotOneMore() {

        User first = data.customer();
        User second = data.customer();
        Product lastOne = data.product("Last One", "500.00", 1);
        data.addToCart(first, lastOne, 1);
        data.addToCart(second, lastOne, 1);

        orderService.checkout(first.getEmail(), ADDRESS);

        assertThrows(InsufficientStockException.class, () -> orderService.checkout(second.getEmail(), ADDRESS));
        assertEquals(0, data.stockOf(lastOne));
    }

    @Test
    void orderKeepsThePricePaid_evenIfTheProductPriceChangesLater() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);
        data.addToCart(user, bottle, 1);

        OrderResponse order = orderService.checkout(user.getEmail(), ADDRESS);

        assertEquals(0, new BigDecimal("750.00").compareTo(order.items().get(0).unitPrice()));
        assertEquals("Bottle", order.items().get(0).name());
    }

    @Test
    void cancel_putsStockBack_andMarksOrderCancelled() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);
        data.addToCart(user, bottle, 4);
        OrderResponse order = orderService.checkout(user.getEmail(), ADDRESS);
        assertEquals(6, data.stockOf(bottle));

        OrderResponse cancelled = orderService.cancelMyOrder(user.getEmail(), order.orderNumber());

        assertEquals("CANCELLED", cancelled.status());
        assertEquals(10, data.stockOf(bottle));
    }

    @Test
    void cancel_twice_givesStockBackOnlyOnce() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);
        data.addToCart(user, bottle, 4);
        OrderResponse order = orderService.checkout(user.getEmail(), ADDRESS);

        orderService.cancelMyOrder(user.getEmail(), order.orderNumber());

        assertThrows(BadRequestException.class,
                () -> orderService.cancelMyOrder(user.getEmail(), order.orderNumber()));
        assertEquals(10, data.stockOf(bottle));
    }

    @Test
    void cancel_isRefusedOnceTheOrderIsPaid() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);
        data.addToCart(user, bottle, 1);
        OrderResponse order = orderService.checkout(user.getEmail(), ADDRESS);

        data.pay(order.orderNumber());

        assertThrows(BadRequestException.class,
                () -> orderService.cancelMyOrder(user.getEmail(), order.orderNumber()));
        assertEquals(9, data.stockOf(bottle));
        assertEquals("PLACED", data.statusOf(order.orderNumber()));
    }

    @Test
    void customerCannotSeeOrCancelSomeoneElsesOrder() {

        User owner = data.customer();
        User stranger = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);
        data.addToCart(owner, bottle, 1);
        OrderResponse order = orderService.checkout(owner.getEmail(), ADDRESS);

        assertThrows(NotFoundException.class,
                () -> orderService.myOrder(stranger.getEmail(), order.orderNumber()));
        assertThrows(NotFoundException.class,
                () -> orderService.cancelMyOrder(stranger.getEmail(), order.orderNumber()));
        assertEquals(9, data.stockOf(bottle));
    }

    @Test
    void status_movesForwardOnly_andOnlyAfterPayment() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);
        data.addToCart(user, bottle, 1);
        OrderResponse order = orderService.checkout(user.getEmail(), ADDRESS);

        // An unpaid order cannot be shipped
        assertThrows(BadRequestException.class,
                () -> orderService.changeStatus(order.orderNumber(), OrderStatus.SHIPPED));

        data.pay(order.orderNumber());

        // Cannot jump straight to DELIVERED
        assertThrows(BadRequestException.class,
                () -> orderService.changeStatus(order.orderNumber(), OrderStatus.DELIVERED));

        assertEquals("SHIPPED", orderService.changeStatus(order.orderNumber(), OrderStatus.SHIPPED).status());
        assertEquals("DELIVERED", orderService.changeStatus(order.orderNumber(), OrderStatus.DELIVERED).status());

        // Cannot go back
        assertThrows(BadRequestException.class,
                () -> orderService.changeStatus(order.orderNumber(), OrderStatus.PLACED));
    }
}
