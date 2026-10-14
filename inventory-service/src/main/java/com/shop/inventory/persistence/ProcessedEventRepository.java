package com.shop.inventory.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, :at)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int tryInsert(UUID eventId, Instant at);
}
