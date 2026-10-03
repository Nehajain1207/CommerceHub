package com.neha.commercehub.order;

import com.neha.commercehub.cart.CartItem;
import com.neha.commercehub.cart.CartItemRepository;
import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.catalog.ProductRepository;
import com.neha.commercehub.common.BadRequestException;
import com.neha.commercehub.common.InsufficientStockException;
import com.neha.commercehub.common.NotFoundException;
import com.neha.commercehub.common.PageResponse;
import com.neha.commercehub.order.OrderDtos.OrderLine;
import com.neha.commercehub.order.OrderDtos.OrderResponse;
import com.neha.commercehub.user.User;
import com.neha.commercehub.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,
                        CartItemRepository cartItemRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository) {

        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
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

    /** A customer can cancel only their own order, and only before it has shipped. */
    @Transactional
    public OrderResponse cancelMyOrder(String email, String orderNumber) {

        Order order = findOwnOrder(email, orderNumber);

        cancel(order);

        return toResponse(order, OrderStatus.CANCELLED);
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> allOrders(int page, int size) {

        int pageSize = Math.min(Math.max(size, 1), 50);

        return PageResponse.from(
                orderRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), pageSize)),
                OrderService::toResponse);
    }

    /** Allowed moves: PLACED to SHIPPED, SHIPPED to DELIVERED, PLACED to CANCELLED. */
    @Transactional
    public OrderResponse changeStatus(String orderNumber, OrderStatus newStatus) {

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found."));

        if (newStatus == OrderStatus.CANCELLED) {

            cancel(order);

        } else {

            OrderStatus required = requiredCurrentStatus(newStatus);

            if (orderRepository.changeStatus(order.getId(), required, newStatus) == 0) {
                throw new BadRequestException(
                        "Order is " + order.getStatus() + " and cannot be moved to " + newStatus + ".");
            }
        }

        return toResponse(order, newStatus);
    }

    // ---------- helpers ----------

    private void cancel(Order order) {

        // Only the request that actually flips PLACED to CANCELLED gives the stock back
        if (orderRepository.changeStatus(order.getId(), OrderStatus.PLACED, OrderStatus.CANCELLED) == 0) {
            throw new BadRequestException("Only orders that have not shipped can be cancelled.");
        }

        for (OrderItem item : order.getItems()) {
            productRepository.releaseStock(item.getProduct().getId(), item.getQuantity());
        }
    }

    private static OrderStatus requiredCurrentStatus(OrderStatus newStatus) {

        if (newStatus == OrderStatus.SHIPPED) {
            return OrderStatus.PLACED;
        }
        if (newStatus == OrderStatus.DELIVERED) {
            return OrderStatus.SHIPPED;
        }
        throw new BadRequestException("An order cannot be moved back to " + newStatus + ".");
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

    private static OrderResponse toResponse(Order order) {
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
