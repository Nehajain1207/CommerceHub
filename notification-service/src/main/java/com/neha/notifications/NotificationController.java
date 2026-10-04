package com.neha.notifications;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record NotificationResponse(
            String orderNumber, String type, String message, String customerEmail, LocalDateTime occurredAt) {
    }

    private final NotificationRepository notificationRepository;
    private final TokenReader tokenReader;

    public NotificationController(NotificationRepository notificationRepository, TokenReader tokenReader) {
        this.notificationRepository = notificationRepository;
        this.tokenReader = tokenReader;
    }

    /** A customer sees only their own updates. The email comes from the verified token, not from the request. */
    @GetMapping
    public List<NotificationResponse> mine(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        TokenReader.Caller caller = tokenReader.read(authorization);

        return notificationRepository.findTop200ByCustomerEmailOrderByIdAsc(caller.email()).stream()
                .map(NotificationController::toResponse)
                .toList();
    }

    /** Admins see the latest activity across all orders. */
    @GetMapping("/all")
    public List<NotificationResponse> all(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        TokenReader.Caller caller = tokenReader.read(authorization);

        if (!caller.admin()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "You are not allowed to see this.");
        }

        return notificationRepository.findTop200ByOrderByIdDesc().stream()
                .map(NotificationController::toResponse)
                .toList();
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getOrderNumber(),
                notification.getType(),
                notification.getMessage(),
                notification.getCustomerEmail(),
                notification.getOccurredAt());
    }
}
