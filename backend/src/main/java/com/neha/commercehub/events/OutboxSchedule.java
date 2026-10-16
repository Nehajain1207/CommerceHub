package com.neha.commercehub.events;

import com.neha.commercehub.scaling.LeaderLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Runs the publisher every second. Only exists when Kafka is switched on.
 *
 * When several copies of the back end run behind the load balancer, only the copy holding
 * the "outbox-publisher" lock sends events. If it dies, the lock expires and another copy
 * takes over within a few seconds.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OutboxSchedule {

    private static final Duration LOCK_TIME = Duration.ofSeconds(5);

    private final OutboxPublisher publisher;
    private final LeaderLock leaderLock;

    public OutboxSchedule(OutboxRepository outboxRepository, EventSender sender, LeaderLock leaderLock) {
        this.publisher = new OutboxPublisher(outboxRepository, sender);
        this.leaderLock = leaderLock;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void run() {

        if (leaderLock.holdOrAcquire("outbox-publisher", LOCK_TIME)) {
            publisher.publishPending();
        }
    }
}
