package com.neha.commercehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.util.TimeZone;

// Login is handled by our own JWT code, so Spring's default generated user is switched off
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CommerceHubApplication {

    public static void main(String[] args) {
        // Windows reports the legacy zone name "Asia/Calcutta", which PostgreSQL rejects
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(CommerceHubApplication.class, args);
    }
}
