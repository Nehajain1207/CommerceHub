package com.neha.commercehub.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Moves an order from one status to another, but only if it is still in the
     * expected status. Returns 1 if it moved and 0 if someone else changed it first.
     * This stops two "cancel" requests from both giving the stock back.
     */
    @Modifying
    @Query("update Order o set o.status = :to where o.id = :orderId and o.status = :from")
    int changeStatus(@Param("orderId") Long orderId,
                     @Param("from") OrderStatus from,
                     @Param("to") OrderStatus to);
}
