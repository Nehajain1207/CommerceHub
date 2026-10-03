package com.neha.commercehub;

import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.common.InsufficientStockException;
import com.neha.commercehub.order.OrderRepository;
import com.neha.commercehub.order.OrderService;
import com.neha.commercehub.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The problem this project is built around: only a few units are left and many
 * customers check out at the same instant. The shop must never sell more than it has.
 */
@SpringBootTest
class StockConcurrencyTest {

    private static final int CUSTOMERS = 50;
    private static final int UNITS_IN_STOCK = 5;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestData data;

    @BeforeEach
    void cleanDatabase() {
        data.clear();
    }

    @Test
    void fiftyCustomersFiveUnits_exactlyFiveOrdersSucceed() throws Exception {

        Product product = data.product("Limited Edition", "999.00", UNITS_IN_STOCK);

        List<User> customers = new ArrayList<>();
        for (int i = 0; i < CUSTOMERS; i++) {
            User customer = data.customer();
            data.addToCart(customer, product, 1);
            customers.add(customer);
        }

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger otherErrors = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(CUSTOMERS);
        CountDownLatch ready = new CountDownLatch(CUSTOMERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CUSTOMERS);

        for (User customer : customers) {

            pool.submit(() -> {

                ready.countDown();

                try {

                    start.await();
                    orderService.checkout(customer.getEmail(), "Test address");
                    succeeded.incrementAndGet();

                } catch (InsufficientStockException ex) {

                    soldOut.incrementAndGet();

                } catch (Exception ex) {

                    otherErrors.incrementAndGet();

                } finally {

                    done.countDown();
                }
            });
        }

        // Release all 50 threads at the same instant
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(UNITS_IN_STOCK, succeeded.get(), "exactly as many orders as units in stock");
        assertEquals(CUSTOMERS - UNITS_IN_STOCK, soldOut.get(), "everyone else is told it is sold out");
        assertEquals(0, otherErrors.get());
        assertEquals(0, data.stockOf(product), "stock ends at zero, never below");
        assertEquals(UNITS_IN_STOCK, orderRepository.count());
    }
}
