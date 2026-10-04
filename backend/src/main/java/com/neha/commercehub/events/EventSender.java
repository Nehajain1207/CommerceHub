package com.neha.commercehub.events;

/**
 * Sends one message and returns only when the receiving system has confirmed it.
 * Throws if it could not be confirmed. Kafka is the real implementation; tests use a fake.
 */
public interface EventSender {

    void send(String key, String payload) throws Exception;
}
