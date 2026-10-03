package com.neha.commercehub.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryResponse(Long id, String name) {
    }

    public record CategoryRequest(@NotBlank String name) {
    }

    public record ProductResponse(
            Long id,
            String sku,
            String name,
            String description,
            BigDecimal price,
            int stock,
            Long categoryId,
            String categoryName,
            boolean active) {
    }

    /** Used to create and edit a product. Stock is set here only when creating. */
    public record ProductRequest(
            @NotBlank String sku,
            @NotBlank String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") BigDecimal price,
            @Min(value = 0, message = "cannot be negative") int stock,
            @NotNull Long categoryId,
            boolean active) {
    }

    /** A positive or negative change, for example +20 after a delivery or -2 for damaged units. */
    public record StockAdjustRequest(@NotNull Integer delta) {
    }
}
