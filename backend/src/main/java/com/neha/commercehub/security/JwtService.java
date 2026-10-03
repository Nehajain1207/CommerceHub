package com.neha.commercehub.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/** Creates and reads login tokens. The signing secret comes from configuration, never from code. */
@Component
public class JwtService {

    private final Key key;
    private final long expiryMillis;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiry-hours}") long expiryHours) {

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = expiryHours * 60 * 60 * 1000;
    }

    public String createToken(String email, String role) {

        Date now = new Date();

        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expiryMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Returns the token's contents, or throws if the token is invalid or expired. */
    public Claims parse(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
