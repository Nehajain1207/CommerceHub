package com.neha.commercehub.catalog;

import com.neha.commercehub.catalog.CatalogDtos.CategoryRequest;
import com.neha.commercehub.catalog.CatalogDtos.CategoryResponse;
import com.neha.commercehub.catalog.CatalogDtos.ProductRequest;
import com.neha.commercehub.catalog.CatalogDtos.ProductResponse;
import com.neha.commercehub.common.BadRequestException;
import com.neha.commercehub.common.NotFoundException;
import com.neha.commercehub.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private static final int MAX_PAGE_SIZE = 48;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    // ---------- shop ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String query, Long categoryId, String sort, int page, int size) {

        String text = query == null ? "" : query.trim();
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), toSort(sort));

        var result = categoryId == null
                ? productRepository.findByActiveTrueAndNameContainingIgnoreCase(text, pageable)
                : productRepository.findByActiveTrueAndCategoryIdAndNameContainingIgnoreCase(categoryId, text, pageable);

        return PageResponse.from(result, ProductService::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getActive(Long id) {

        Product product = find(id);

        if (!product.isActive()) {
            throw new NotFoundException("Product not found.");
        }

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {

        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName()))
                .toList();
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> adminSearch(String query, int page, int size) {

        String text = query == null ? "" : query.trim();
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by("name"));

        return PageResponse.from(
                productRepository.findByNameContainingIgnoreCase(text, pageable),
                ProductService::toResponse);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {

        String sku = request.sku().trim().toUpperCase();

        if (productRepository.existsBySku(sku)) {
            throw new BadRequestException("A product with SKU " + sku + " already exists.");
        }

        Product product = new Product(
                sku,
                request.name().trim(),
                request.description(),
                request.price(),
                request.stock(),
                findCategory(request.categoryId()));

        product.setActive(request.active());

        return toResponse(productRepository.save(product));
    }

    /** Edits product details. Stock is NOT changed here; use adjustStock. */
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {

        Product product = find(id);
        String sku = request.sku().trim().toUpperCase();

        if (!sku.equals(product.getSku()) && productRepository.existsBySku(sku)) {
            throw new BadRequestException("A product with SKU " + sku + " already exists.");
        }

        product.setSku(sku);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(findCategory(request.categoryId()));
        product.setActive(request.active());

        return toResponse(product);
    }

    @Transactional
    public ProductResponse adjustStock(Long id, int delta) {

        Product product = find(id);

        if (productRepository.adjustStock(id, delta) == 0) {
            throw new BadRequestException("Stock cannot go below zero.");
        }

        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                productRepository.currentStock(id),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.isActive());
    }

    /** Hides the product from the shop. It is kept so old orders still show it. */
    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> lowStock(int threshold) {

        return productRepository.findByActiveTrueAndStockLessThanEqualOrderByStockAsc(threshold).stream()
                .map(ProductService::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {

        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Category " + name + " already exists.");
        }

        Category saved = categoryRepository.save(new Category(name));
        return new CategoryResponse(saved.getId(), saved.getName());
    }

    // ---------- helpers ----------

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found."));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found."));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /** Only these sort options are accepted, so a caller cannot sort by an arbitrary column. */
    private static Sort toSort(String sort) {

        if ("price_asc".equals(sort)) {
            return Sort.by("price").ascending();
        }
        if ("price_desc".equals(sort)) {
            return Sort.by("price").descending();
        }
        if ("name".equals(sort)) {
            return Sort.by("name").ascending();
        }
        return Sort.by("createdAt").descending().and(Sort.by("id").descending());
    }

    public static ProductResponse toResponse(Product product) {

        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.isActive());
    }
}
