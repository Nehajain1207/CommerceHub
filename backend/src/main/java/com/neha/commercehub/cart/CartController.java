package com.neha.commercehub.cart;

import com.neha.commercehub.cart.CartDtos.AddItemRequest;
import com.neha.commercehub.cart.CartDtos.CartResponse;
import com.neha.commercehub.cart.CartDtos.UpdateQuantityRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in user's own cart. authentication.getName() is the email taken from the login token. */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(Authentication authentication) {
        return cartService.getCart(authentication.getName());
    }

    @PostMapping("/items")
    public CartResponse add(@Valid @RequestBody AddItemRequest request, Authentication authentication) {
        return cartService.addItem(authentication.getName(), request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    public CartResponse update(@PathVariable Long productId,
                               @Valid @RequestBody UpdateQuantityRequest request,
                               Authentication authentication) {

        return cartService.updateQuantity(authentication.getName(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse remove(@PathVariable Long productId, Authentication authentication) {
        return cartService.removeItem(authentication.getName(), productId);
    }
}
