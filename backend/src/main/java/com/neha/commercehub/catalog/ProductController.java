package com.neha.commercehub.catalog;

import com.neha.commercehub.catalog.CatalogDtos.CategoryResponse;
import com.neha.commercehub.catalog.CatalogDtos.ProductResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.neha.commercehub.common.PageResponse;
import com.neha.commercehub.scaling.CatalogCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public shop endpoints: no login needed.
 *
 * These are most of the shop's traffic, so they are answered from the Redis cache when
 * possible. The cache is used here, outside any database transaction, so a cache hit does
 * not even borrow a database connection.
 */
@RestController
@RequestMapping("/api")
public class ProductController {

    private static final TypeReference<PageResponse<ProductResponse>> PAGE = new TypeReference<>() { };
    private static final TypeReference<ProductResponse> PRODUCT = new TypeReference<>() { };
    private static final TypeReference<List<CategoryResponse>> CATEGORIES = new TypeReference<>() { };

    private final ProductService productService;
    private final CatalogCache cache;

    public ProductController(ProductService productService, CatalogCache cache) {
        this.productService = productService;
        this.cache = cache;
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        String text = q == null ? "" : q.trim().toLowerCase();
        String key = text + "|" + categoryId + "|" + sort + "|" + page + "|" + size;

        return cache.get("products", key, PAGE, () -> productService.search(q, categoryId, sort, page, size));
    }

    @GetMapping("/products/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return cache.get("product", String.valueOf(id), PRODUCT, () -> productService.getActive(id));
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return cache.get("categories", "all", CATEGORIES, productService::categories);
    }
}
