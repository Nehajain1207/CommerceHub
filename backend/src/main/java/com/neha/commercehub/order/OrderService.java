package com.neha.commercehub.order;

import com.neha.commercehub.cart.CartItem;
import com.neha.commercehub.cart.CartItemRepository;
import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.catalog.ProductRepository;
import com.neha.commercehub.common.BadRequestException;
import com.neha.commercehub.common.InsufficientStockException;
import com.neha.commercehub.common.NotFoundException;
import com.neha.commercehub.common.PageResponse;
import com.neha.commercehub.events.OrderEventType;
import com.neha.commercehub.events.OutboxWriter;
import com.neha.commercehub.order.OrderDtos.OrderLine;
import com.neha.commercehub.order.OrderDtos.OrderResponse;
import com.neha.commercehub.scaling.StockGate;
import com.neha.commercehub.user.User;
import com.neha.commercehub.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OutboxWriter outbox;
    private final StockGate stockGate;

    public OrderService(OrderRepository orderRepository,
                        CartItemRepository cartItemRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository,
                        OutboxWriter outbox,
                        StockGate stockGate) {

        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.outbox = outbox;
        this.stockGate = stockGate;
    }

    /** The user's cart lines, sorted by product id (the same order checkout locks rows in). */
    @Transactional(readOnly = true)
    public List<CheckoutLine> checkoutLines(String email) {

        return cartItemRepository.findCheckoutLines(email);
    }

    /** Exact stock from the database; used to (re)load the stock gate's counter. */
    @Transactional(readOnly = true)
    public int currentStock(Long productId) {
        return productRepository.currentStock(productId);
    }

    /**
     * Turns the user's cart into an order.
     *
     * The important part is stock. For every product, reserveStock runs one UPDATE
     * that subtracts the quantity ONLY IF enough is left. If any product is short,
     * an exception is thrown and the whole transaction rolls back, which also puts
     * back the stock already taken for the earlier products. So an order is either
     * placed completely or not at all, and stock can never go below zero.
     */
    @Transactional
    public OrderResponse checkout(String email, String shippingAddress) {

        User user = findUser(email);

        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderByIdAsc(user.getId());

        if (cartItems.isEmpty()) {
            throw new BadRequestException("Your cart is empty.");
        }

        // Always reserve in the same order (by product id). If two orders locked the
        // same products in opposite orders they could wait on each other forever.
        List<CartItem> sorted = cartItems.stream()
                .sorted(Comparator.comparing((CartItem item) -> item.getProduct().getId()))
                .toList();

        Order order = new Order(newOrderNumber(), user, shippingAddress.trim());

        for (CartItem item : sorted) {

            Product product = item.getProduct();

            if (!product.isActive()) {
                throw new BadRequestException(product.getName() + " is no longer available.");
            }

            int reserved = productRepository.reserveStock(product.getId(), item.getQuantity());

            if (reserved == 0) {
                throw new InsufficientStockException(product.getName());
            }

            order.addItem(new OrderItem(order, product, item.getQuantity()));
        }

        Order saved = orderRepository.save(order);

        // Saved in this same transaction: if the order rolls back, so does the event
        outbox.record(OrderEventType.ORDER_CREATED, saved);

        cartItemRepository.deleteByUserId(user.getId());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(String email) {

        return orderRepository.findByUserIdOrderByCreatedAtDesc(findUser(email).getId()).stream()
                .map(OrderService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse myOrder(String email, String orderNumber) {
        return toResponse(findOwnOrder(email, orderNumber));
    }

    /**
     * A customer can cancel their own order only while it is still unpaid.
     * (Cancelling a paid order would need a refund, which is not built yet.)
     */
    @Transactional
    public OrderResponse cancelMyOrder(String email, String orderNumber) {

        Order order = findOwnOrder(email, orderNumber);

        cancelUnpaid(order);

        return toResponse(order, OrderStatus.CANCELLED);
    }

    /** Used by the payment code: looks an order up and checks it belongs to this customer. */
    @Transactional(readOnly = true)
    public Order requireOwnOrder(String email, String orderNumber) {
        return findOwnOrder(email, orderNumber);
    }

    /**
     * Marks an unpaid order as paid. Returns true if THIS call made the change and
     * false if the order was already paid, so a repeated confirmation is harmless.
     */
    @Transactional
    public boolean markPaid(Long orderId) {

        if (orderRepository.changeStatus(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.PLACED) == 0) {
            return false;
        }

        // Only the call that actually changed the status announces it, so "paid" is announced once
        orderRepository.findById(orderId)
                .ifPresent(order -> outbox.record(OrderEventType.ORDER_PAID, order));

        return true;
    }

    /** Cancels an unpaid order and gives its stock back. Returns false if it was no longer unpaid. */
    @Transactional
    public boolean cancelUnpaidById(Long orderId) {

        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null
                || orderRepository.changeStatus(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED) == 0) {
            return false;
        }

        releaseStock(order);
        outbox.record(OrderEventType.ORDER_CANCELLED, order);
        return true;
    }

    /** Ids of orders that have waited for payment longer than allowed. */
    @Transactional(readOnly = true)
    public List<Long> unpaidOrderIdsOlderThan(LocalDateTime createdBefore) {

        return orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, createdBefore).stream()
                .map(Order::getId)
                .toList();
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> allOrders(int page, int size) {

        int pageSize = Math.min(Math.max(size, 1), 50);

        return PageResponse.from(
                orderRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), pageSize)),
                OrderService::toResponse);
    }

    /** Allowed moves: PLACED to SHIPPED, SHIPPED to DELIVERED, and unpaid to CANCELLED. */
    @Transactional
    public OrderResponse changeStatus(String orderNumber, OrderStatus newStatus) {

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found."));

        if (newStatus == OrderStatus.CANCELLED) {

            cancelUnpaid(order);

        } else {

            OrderStatus required = requiredCurrentStatus(newStatus);

            if (orderRepository.changeStatus(order.getId(), required, newStatus) == 0) {
                throw new BadRequestException(
                        "Order is " + label(order.getStatus()) + " and cannot be moved to " + label(newStatus) + ".");
            }

            outbox.record(newStatus == OrderStatus.SHIPPED
                    ? OrderEventType.ORDER_SHIPPED
                    : OrderEventType.ORDER_DELIVERED, order);
        }

        return toResponse(order, newStatus);
    }

    // ---------- helpers ----------

    private void cancelUnpaid(Order order) {

        // Only the request that actually flips the status gives the stock back
        if (orderRepository.changeStatus(order.getId(), OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED) == 0) {
            throw new BadRequestException("Only orders that are still waiting for payment can be cancelled.");
        }

        releaseStock(order);
        outbox.record(OrderEventType.ORDER_CANCELLED, order);
    }

    private void releaseStock(Order order) {

        for (OrderItem item : order.getItems()) {
            productRepository.releaseStock(item.getProduct().getId(), item.getQuantity());

            // Stock went up, so the gate's counter is reloaded from the database after this commits
            stockGate.forgetAfterCommit(item.getProduct().getId());
        }
    }

    private static String label(OrderStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
    }

    private static OrderStatus requiredCurrentStatus(OrderStatus newStatus) {

        if (newStatus == OrderStatus.SHIPPED) {
            return OrderStatus.PLACED;
        }
        if (newStatus == OrderStatus.DELIVERED) {
            return OrderStatus.SHIPPED;
        }
        throw new BadRequestException("An order cannot be moved to " + label(newStatus) + " by hand.");
    }

    private Order findOwnOrder(String email, String orderNumber) {

        // "Not found" for someone else's order too, so order numbers cannot be probed
        return orderRepository.findByOrderNumber(orderNumber)
                .filter(order -> order.getUser().getEmail().equals(email))
                .orElseThrow(() -> new NotFoundException("Order not found."));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    private static String newOrderNumber() {
        return "CH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static OrderResponse toResponse(Order order) {
        return toResponse(order, order.getStatus());
    }

    /** The status is passed in because a status change is done by a direct UPDATE, not on the object. */
    private static OrderResponse toResponse(Order order, OrderStatus status) {

        List<OrderLine> lines = order.getItems().stream()
                .map(item -> new OrderLine(
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineTotal()))
                .toList();

        return new OrderResponse(
                order.getOrderNumber(),
                status.name(),
                order.getTotal(),
                order.getShippingAddress(),
                order.getCreatedAt(),
                order.getUser().getName(),
                order.getUser().getEmail(),
                lines);
    }
}
