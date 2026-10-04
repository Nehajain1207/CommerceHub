package com.neha.commercehub.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /** The oldest events not yet sent, oldest first, so they go out in the order they happened. */
    List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByIdAsc();

    List<OutboxEvent> findByOrderNumberOrderByIdAsc(String orderNumber);

    long countByPublishedAtIsNull();

    @Transactional
    @Modifying
    @Query("update OutboxEvent e set e.publishedAt = :publishedAt where e.id = :id and e.publishedAt is null")
    int markPublished(@Param("id") Long id, @Param("publishedAt") LocalDateTime publishedAt);
}
