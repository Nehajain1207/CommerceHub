package com.neha.commercehub.order;

/**
 * PENDING_PAYMENT  order created and stock held, waiting for the customer to pay
 * PLACED           paid; waiting to be shipped
 * SHIPPED, DELIVERED
 * CANCELLED        cancelled by the customer, the admin, or because payment never arrived
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PLACED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
