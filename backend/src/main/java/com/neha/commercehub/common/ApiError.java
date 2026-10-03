package com.neha.commercehub.common;

import java.time.LocalDateTime;

/** The JSON body returned for every error, so the front end can always show "message". */
public record ApiError(int status, String error, String message, LocalDateTime timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, LocalDateTime.now());
    }
}
