package com.shop.inventory.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {

    @Query(value = """
            SELECT * FROM outbox WHERE published_at IS NULL ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntity> lockUnpublished(int limit);

    @Modifying
    @Query("UPDATE OutboxEntity o SET o.publishedAt = :at WHERE o.eventId = :eventId")
    int markPublished(UUID eventId, Instant at);

    long countByPublishedAtIsNull();
}
