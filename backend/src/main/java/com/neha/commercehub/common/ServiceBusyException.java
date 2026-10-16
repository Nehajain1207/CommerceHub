package com.neha.commercehub.common;

/** The server is at capacity for this kind of work right now. Answered with HTTP 503 ("try again"). */
public class ServiceBusyException extends RuntimeException {

    public ServiceBusyException(String message) {
        super(message);
    }
}
