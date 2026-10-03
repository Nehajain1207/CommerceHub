package com.neha.commercehub.order;

import com.neha.commercehub.common.PageResponse;
import com.neha.commercehub.order.OrderDtos.OrderResponse;
import com.neha.commercehub.order.OrderDtos.StatusRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Order management. Needs the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderResponse> all(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return orderService.allOrders(page, size);
    }

    @PatchMapping("/{orderNumber}/status")
    public OrderResponse changeStatus(@PathVariable String orderNumber,
                                      @Valid @RequestBody StatusRequest request) {

        return orderService.changeStatus(orderNumber, request.status());
    }
}
