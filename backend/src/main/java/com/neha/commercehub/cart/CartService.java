package com.neha.commercehub.cart;

import com.neha.commercehub.cart.CartDtos.CartLine;
import com.neha.commercehub.cart.CartDtos.CartResponse;
import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.catalog.ProductRepository;
import com.neha.commercehub.common.BadRequestException;
import com.neha.commercehub.common.NotFoundException;
import com.neha.commercehub.user.User;
import com.neha.commercehub.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart is only a wish list: adding to cart does NOT reserve stock.
 * Stock is taken at checkout (see OrderService), which is where it must be exact.
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       UserRepository userRepository) {

        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(String email) {
        return toResponse(cartItemRepository.findByUserIdOrderByIdAsc(findUser(email).getId()));
    }

    @Transactional
    public CartResponse addItem(String email, Long productId, int quantity) {

        User user = findUser(email);

        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Product not found."));

        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElse(null);

        int newQuantity = (item == null ? 0 : item.getQuantity()) + quantity;

        if (newQuantity > product.getStock()) {
            throw new BadRequestException("Only " + product.getStock() + " left of " + product.getName() + ".");
        }

        if (item == null) {
            cartItemRepository.save(new CartItem(user, product, newQuantity));
        } else {
            item.setQuantity(newQuantity);
        }

        return toResponse(cartItemRepository.findByUserIdOrderByIdAsc(user.getId()));
    }

    @Transactional
    public CartResponse updateQuantity(String email, Long productId, int quantity) {

        User user = findUser(email);

        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new NotFoundException("This product is not in your cart."));

        if (quantity == 0) {

            cartItemRepository.delete(item);

        } else {

            if (quantity > item.getProduct().getStock()) {
                throw new BadRequestException(
                        "Only " + item.getProduct().getStock() + " left of " + item.getProduct().getName() + ".");
            }

            item.setQuantity(quantity);
        }

        cartItemRepository.flush();

        return toResponse(cartItemRepository.findByUserIdOrderByIdAsc(user.getId()));
    }

    @Transactional
    public CartResponse removeItem(String email, Long productId) {
        return updateQuantity(email, productId, 0);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    private CartResponse toResponse(List<CartItem> items) {

        List<CartLine> lines = items.stream()
                .map(item -> {
                    Product product = item.getProduct();
                    BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                    return new CartLine(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            item.getQuantity(),
                            product.getStock(),
                            lineTotal);
                })
                .toList();

        int itemCount = lines.stream().mapToInt(CartLine::quantity).sum();
        BigDecimal total = lines.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(lines, itemCount, total);
    }
}
