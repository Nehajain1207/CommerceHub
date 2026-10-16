package com.neha.commercehub.order;

/** One cart line as needed by the stock gate, read before the order transaction starts. */
public record CheckoutLine(Long productId, String productName, Integer quantity) {
}
