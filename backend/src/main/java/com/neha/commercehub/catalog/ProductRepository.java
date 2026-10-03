package com.neha.commercehub.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // ----- shop (active products only) -----

    Page<Product> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryIdAndNameContainingIgnoreCase(
            Long categoryId, String name, Pageable pageable);

    // ----- admin -----

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Product> findByActiveTrueAndStockLessThanEqualOrderByStockAsc(int threshold);

    boolean existsBySku(String sku);

    // ----- stock -----
    //
    // Stock is never changed by "read the number, change it in Java, save it back",
    // because two orders doing that at the same moment overwrite each other.
    // Each change is a single UPDATE that the database runs as one step.

    /**
     * Takes units out of stock, but only if enough are left.
     * Returns 1 if the stock was reserved and 0 if there was not enough.
     */
    @Modifying
    @Query("update Product p set p.stock = p.stock - :quantity "
            + "where p.id = :productId and p.stock >= :quantity")
    int reserveStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /** Puts units back into stock (cancelled order). */
    @Modifying
    @Query("update Product p set p.stock = p.stock + :quantity where p.id = :productId")
    int releaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * Admin stock correction by a positive or negative amount.
     * Returns 0 if it would take the stock below zero.
     */
    @Modifying
    @Query("update Product p set p.stock = p.stock + :delta "
            + "where p.id = :productId and p.stock + :delta >= 0")
    int adjustStock(@Param("productId") Long productId, @Param("delta") int delta);

    @Query("select p.stock from Product p where p.id = :productId")
    int currentStock(@Param("productId") Long productId);
}
