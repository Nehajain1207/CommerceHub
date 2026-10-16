package com.neha.commercehub.user;

import com.neha.commercehub.user.AuthDtos.AuthResponse;
import com.neha.commercehub.user.AuthDtos.LoginRequest;
import com.neha.commercehub.user.AuthDtos.RegisterRequest;
import com.neha.commercehub.scaling.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final int loginLimit;
    private final Duration loginWindow;

    public AuthController(AuthService authService,
                          RateLimiter rateLimiter,
                          @Value("${app.ratelimit.login.limit:10}") int loginLimit,
                          @Value("${app.ratelimit.login.window-seconds:60}") long loginWindowSeconds) {

        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.loginLimit = loginLimit;
        this.loginWindow = Duration.ofSeconds(loginWindowSeconds);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        // Slows down password guessing against one account
        rateLimiter.check("login", request.email(), loginLimit, loginWindow,
                "Too many login attempts for this account. Please wait a minute and try again.");

        return authService.login(request);
    }
}
