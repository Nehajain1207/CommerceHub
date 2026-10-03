package com.neha.commercehub;

import com.neha.commercehub.cart.CartDtos.CartResponse;
import com.neha.commercehub.cart.CartService;
import com.neha.commercehub.catalog.CatalogDtos.ProductRequest;
import com.neha.commercehub.catalog.CatalogDtos.ProductResponse;
import com.neha.commercehub.catalog.Product;
import com.neha.commercehub.catalog.ProductService;
import com.neha.commercehub.common.BadRequestException;
import com.neha.commercehub.common.NotFoundException;
import com.neha.commercehub.security.JwtService;
import com.neha.commercehub.user.AuthDtos.AuthResponse;
import com.neha.commercehub.user.AuthDtos.LoginRequest;
import com.neha.commercehub.user.AuthDtos.RegisterRequest;
import com.neha.commercehub.user.AuthService;
import com.neha.commercehub.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sign-up and login, product management and the cart. */
@SpringBootTest
class AuthAndCatalogTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CartService cartService;

    @Autowired
    private TestData data;

    @BeforeEach
    void cleanDatabase() {
        data.clear();
    }

    // ---------- sign-up and login ----------

    @Test
    void register_createsCustomer_andReturnsAWorkingToken() {

        AuthResponse response = authService.register(new RegisterRequest("Neha", "Neha@Example.com", "secret123"));

        assertEquals("CUSTOMER", response.role());
        assertEquals("neha@example.com", response.email());
        assertEquals("neha@example.com", jwtService.parse(response.token()).getSubject());
        assertEquals("CUSTOMER", jwtService.parse(response.token()).get("role", String.class));
    }

    @Test
    void register_refusesAnEmailThatIsAlreadyUsed() {

        authService.register(new RegisterRequest("Neha", "neha@example.com", "secret123"));

        assertThrows(BadRequestException.class,
                () -> authService.register(new RegisterRequest("Other", "neha@example.com", "another123")));
    }

    @Test
    void login_worksWithTheRightPassword_andFailsWithTheWrongOne() {

        authService.register(new RegisterRequest("Neha", "neha@example.com", "secret123"));

        assertFalse(authService.login(new LoginRequest("neha@example.com", "secret123")).token().isBlank());

        assertThrows(BadRequestException.class,
                () -> authService.login(new LoginRequest("neha@example.com", "wrong-password")));
        assertThrows(BadRequestException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", "secret123")));
    }

    @Test
    void tamperedTokenIsRejected() {

        AuthResponse response = authService.register(new RegisterRequest("Neha", "neha@example.com", "secret123"));

        assertThrows(Exception.class, () -> jwtService.parse(response.token() + "x"));
    }

    // ---------- products ----------

    @Test
    void shopSearch_findsByName_andHidesInactiveProducts() {

        Product bottle = data.product("Steel Bottle", "750.00", 10);
        data.product("Glass Bottle", "450.00", 10);
        data.product("Kettle", "1100.00", 10);

        assertEquals(2, productService.search("bottle", null, "newest", 0, 12).totalElements());

        productService.deactivate(bottle.getId());

        assertEquals(1, productService.search("bottle", null, "newest", 0, 12).totalElements());
        assertThrows(NotFoundException.class, () -> productService.getActive(bottle.getId()));
    }

    @Test
    void shopSearch_sortsByPrice() {

        data.product("Mid", "500.00", 1);
        data.product("Cheap", "100.00", 1);
        data.product("Costly", "900.00", 1);

        var result = productService.search(null, null, "price_asc", 0, 12);

        assertEquals("Cheap", result.content().get(0).name());
        assertEquals("Costly", result.content().get(2).name());
    }

    @Test
    void editingAProduct_doesNotChangeItsStock() {

        Product bottle = data.product("Bottle", "750.00", 10);

        // The edit form sends stock 999, which must be ignored on update
        ProductResponse updated = productService.update(bottle.getId(), new ProductRequest(
                bottle.getSku(), "Bottle 1L", "Bigger", new BigDecimal("800.00"), 999,
                bottle.getCategory().getId(), true));

        assertEquals("Bottle 1L", updated.name());
        assertEquals(10, data.stockOf(bottle));
    }

    @Test
    void adjustStock_addsAndRemoves_butNeverGoesBelowZero() {

        Product bottle = data.product("Bottle", "750.00", 10);

        assertEquals(30, productService.adjustStock(bottle.getId(), 20).stock());
        assertEquals(25, productService.adjustStock(bottle.getId(), -5).stock());

        assertThrows(BadRequestException.class, () -> productService.adjustStock(bottle.getId(), -26));
        assertEquals(25, data.stockOf(bottle));
    }

    @Test
    void lowStock_listsOnlyProductsAtOrBelowTheThreshold() {

        data.product("Plenty", "100.00", 50);
        data.product("Few", "100.00", 3);
        data.product("None", "100.00", 0);

        var low = productService.lowStock(5);

        assertEquals(2, low.size());
        assertEquals("None", low.get(0).name());
    }

    // ---------- cart ----------

    @Test
    void cart_addsUpQuantities_andCalculatesTheTotal() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);

        cartService.addItem(user.getEmail(), bottle.getId(), 1);
        CartResponse cart = cartService.addItem(user.getEmail(), bottle.getId(), 2);

        assertEquals(1, cart.items().size());
        assertEquals(3, cart.itemCount());
        assertEquals(0, new BigDecimal("2250.00").compareTo(cart.total()));
    }

    @Test
    void cart_refusesMoreThanIsInStock() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 2);

        assertThrows(BadRequestException.class, () -> cartService.addItem(user.getEmail(), bottle.getId(), 3));
    }

    @Test
    void cart_quantityZeroRemovesTheItem() {

        User user = data.customer();
        Product bottle = data.product("Bottle", "750.00", 10);
        cartService.addItem(user.getEmail(), bottle.getId(), 2);

        CartResponse cart = cartService.updateQuantity(user.getEmail(), bottle.getId(), 0);

        assertTrue(cart.items().isEmpty());
    }
}
