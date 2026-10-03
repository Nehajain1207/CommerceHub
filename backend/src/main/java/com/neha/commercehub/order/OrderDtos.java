package com.neha.commercehub.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutRequest(@NotBlank @Size(max = 500) String shippingAddress) {
    }

    public record StatusRequest(@NotNull OrderStatus status) {
    }

    public record OrderLine(Long productId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public record OrderResponse(
            String orderNumber,
            String status,
            BigDecimal total,
            String shippingAddress,
            LocalDateTime createdAt,
            String customerName,
            String customerEmail,
            List<OrderLine> items) {
    }
}
