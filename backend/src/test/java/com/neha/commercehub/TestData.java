package com.neha.commercehub;

import com.neha.commercehub.cart.CartItem;
import com.neha.commercehub.cart.CartItemRepository;
import com.neha.commercehub.catalog.Category;
import com.neha.commercehub.catalog.CategoryRepository;
import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.catalog.ProductRepository;
import com.neha.commercehub.order.OrderRepository;
import com.neha.commercehub.user.Role;
import com.neha.commercehub.user.User;
import com.neha.commercehub.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Small helpers the tests use to create users, products and carts. */
@Component
public class TestData {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    /** Empties every table, in an order that respects the links between them. */
    public void clear() {
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    public User customer() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        return userRepository.save(new User("Test User", email, "not-a-real-hash", Role.CUSTOMER));
    }

    public Product product(String name, String price, int stock) {

        Category category = categoryRepository.save(new Category("Category " + UUID.randomUUID()));

        return productRepository.save(new Product(
                "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                name,
                "Test product",
                new BigDecimal(price),
                stock,
                category));
    }

    public void addToCart(User user, Product product, int quantity) {
        cartItemRepository.save(new CartItem(user, product, quantity));
    }

    public int stockOf(Product product) {
        return productRepository.currentStock(product.getId());
    }
}
