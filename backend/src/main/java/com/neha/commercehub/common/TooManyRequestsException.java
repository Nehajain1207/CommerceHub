package com.neha.commercehub.common;

/** The caller is sending requests too quickly. Answered with HTTP 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
