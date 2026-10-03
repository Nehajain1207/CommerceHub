package com.neha.commercehub.order;

import com.neha.commercehub.order.OrderDtos.CheckoutRequest;
import com.neha.commercehub.order.OrderDtos.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The logged-in customer's own orders. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@Valid @RequestBody CheckoutRequest request, Authentication authentication) {
        return orderService.checkout(authentication.getName(), request.shippingAddress());
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
