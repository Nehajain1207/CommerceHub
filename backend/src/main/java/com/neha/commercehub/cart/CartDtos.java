package com.neha.commercehub.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddItemRequest(
            @NotNull Long productId,
            @Min(value = 1, message = "must be at least 1") int quantity) {
    }

    /** Quantity 0 removes the item. */
    public record UpdateQuantityRequest(@Min(value = 0, message = "cannot be negative") int quantity) {
    }

    public record CartLine(
            Long productId,
            String name,
            BigDecimal unitPrice,
            int quantity,
            int stock,
            BigDecimal lineTotal) {
    }

    public record CartResponse(List<CartLine> items, int itemCount, BigDecimal total) {
    }
}
