package com.neha.commercehub.payment;

import com.neha.commercehub.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One payment attempt for one order, as known to the payment gateway. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    /** The gateway's own id for this order (Razorpay: "order_..."). */
    @Column(nullable = false, unique = true)
    private String gatewayOrderId;

    /** The gateway's id for the successful payment (Razorpay: "pay_..."). Empty until paid. */
    private String gatewayPaymentId;

    /** Amount in paise (1 rupee = 100 paise), which is how gateways expect it. Decided by the server. */
    @Column(nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime paidAt;

    public Payment() {
    }

    public Payment(Order order, String gatewayOrderId, long amountPaise) {
        this.order = order;
        this.gatewayOrderId = gatewayOrderId;
        this.amountPaise = amountPaise;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }
}
