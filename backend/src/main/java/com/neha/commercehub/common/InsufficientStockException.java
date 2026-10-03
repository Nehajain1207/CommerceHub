package com.neha.commercehub.common;

/** Thrown when an order asks for more units than are left. Rolls the whole order back. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productName) {
        super("Not enough stock for " + productName + ".");
    }
}
