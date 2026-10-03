package com.neha.commercehub.payment;

/**
 * CREATED       waiting for the customer to pay
 * PAID          money received and the order confirmed
 * EXPIRED       the customer never paid; the order was cancelled and stock freed
 * NEEDS_REFUND  money arrived after the order had already been cancelled
 */
public enum PaymentStatus {
    CREATED,
    PAID,
    EXPIRED,
    NEEDS_REFUND
}
