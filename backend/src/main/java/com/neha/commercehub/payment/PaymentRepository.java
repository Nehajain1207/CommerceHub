package com.neha.commercehub.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    /**
     * Records a successful payment, but only if this payment is still waiting.
     * Returns 1 for the first confirmation and 0 for any repeat, which is what makes
     * "the browser and the gateway both tell us about the same payment" safe.
     */
    @Modifying
    @Query("update Payment p set p.status = :newStatus, p.gatewayPaymentId = :gatewayPaymentId, p.paidAt = :paidAt "
            + "where p.id = :paymentId and p.status = :expected")
    int recordPayment(@Param("paymentId") Long paymentId,
                      @Param("gatewayPaymentId") String gatewayPaymentId,
                      @Param("paidAt") LocalDateTime paidAt,
                      @Param("expected") PaymentStatus expected,
                      @Param("newStatus") PaymentStatus newStatus);

    @Modifying
    @Query("update Payment p set p.status = :to where p.id = :paymentId and p.status = :from")
    int changeStatus(@Param("paymentId") Long paymentId,
                     @Param("from") PaymentStatus from,
                     @Param("to") PaymentStatus to);
}
