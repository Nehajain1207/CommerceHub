package com.neha.commercehub.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import com.neha.commercehub.order.CheckoutLine;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByIdAsc(Long userId);

    /**
     * The cart as plain lines for the stock gate: one quick query, sorted by product id
     * (the same order checkout locks product rows in).
     */
    @Query("select new com.neha.commercehub.order.CheckoutLine(p.id, p.name, ci.quantity) "
            + "from CartItem ci join ci.product p join ci.user u "
            + "where u.email = :email order by p.id")
    List<CheckoutLine> findCheckoutLines(@Param("email") String email);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserId(Long userId);
}
