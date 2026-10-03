package com.neha.commercehub.order;

import com.neha.commercehub.order.OrderDtos.OrderResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The logged-in customer's own orders. Placing an order is in PaymentController (/api/checkout). */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> myOrders(Authentication authentication) {
        return orderService.myOrders(authentication.getName());
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse myOrder(@PathVariable String orderNumber, Authentication authentication) {
        return orderService.myOrder(authentication.getName(), orderNumber);
    }

    @PostMapping("/{orderNumber}/cancel")
    public OrderResponse cancel(@PathVariable String orderNumber, Authentication authentication) {
        return orderService.cancelMyOrder(authentication.getName(), orderNumber);
    }
}
