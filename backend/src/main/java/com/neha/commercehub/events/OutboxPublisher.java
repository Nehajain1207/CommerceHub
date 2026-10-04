package com.neha.commercehub.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Moves events from the outbox table to Kafka.
 *
 * For each unsent row, oldest first: send it, wait for confirmation, then mark it
 * sent. If sending fails (Kafka is down, the network drops), it stops and tries
 * again on the next run, so nothing is skipped and order is kept.
 *
 * One consequence: if the app crashes after Kafka confirmed but before the row was
 * marked, that event is sent again next time. So an event is delivered AT LEAST
 * once, and receivers must ignore an eventId they have already handled.
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final EventSender sender;
    private boolean lastRunFailed;

    public OutboxPublisher(OutboxRepository outboxRepository, EventSender sender) {
        this.outboxRepository = outboxRepository;
        this.sender = sender;
    }

    /** Returns how many events were sent in this run. */
    public synchronized int publishPending() {

        List<OutboxEvent> pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByIdAsc();
        int sent = 0;

        for (OutboxEvent event : pending) {

            try {

                sender.send(event.getOrderNumber(), event.getPayload());

            } catch (Exception ex) {

                if (!lastRunFailed) {
                    log.warn("Could not send events to Kafka ({}). {} event(s) are kept and will be retried.",
                            ex.getClass().getSimpleName(), outboxRepository.countByPublishedAtIsNull());
                }
                lastRunFailed = true;
                return sent;
            }

            outboxRepository.markPublished(event.getId(), LocalDateTime.now());
            sent++;
        }

        if (lastRunFailed && !pending.isEmpty()) {
            log.info("Kafka is reachable again; sent {} waiting event(s).", sent);
        }
        lastRunFailed = false;

        return sent;
    }
}
