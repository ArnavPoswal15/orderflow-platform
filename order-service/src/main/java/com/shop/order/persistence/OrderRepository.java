package com.shop.order.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.idempotencyKey = :key")
    Optional<OrderEntity> findByIdempotencyKeyWithItems(@Param("key") String idempotencyKey);

    Page<OrderEntity> findByStatus(String status, Pageable pageable);

    @Query("SELECT o.status, COUNT(o) FROM OrderEntity o GROUP BY o.status")
    List<Object[]> countGroupedByStatus();
}
