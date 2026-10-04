package com.neha.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        // Windows reports the legacy zone name "Asia/Calcutta", which PostgreSQL rejects
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
