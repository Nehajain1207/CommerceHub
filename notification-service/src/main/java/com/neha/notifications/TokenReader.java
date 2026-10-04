package com.neha.notifications;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Key;

/**
 * Checks the login token issued by the shop back end. Both services share the
 * signing secret, so this service can verify a token without calling the shop.
 */
@Component
public class TokenReader {

    public record Caller(String email, boolean admin) {
    }

    private final Key key;

    public TokenReader(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Caller read(String authorizationHeader) {

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
        }

        try {

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(authorizationHeader.substring(7))
                    .getBody();

            return new Caller(claims.getSubject(), "ADMIN".equals(claims.get("role", String.class)));

        } catch (Exception ex) {

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }
    }
}
