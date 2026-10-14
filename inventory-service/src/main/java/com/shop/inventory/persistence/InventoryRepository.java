package com.shop.inventory.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {

    List<InventoryEntity> findBySkuIn(List<String> skus);

    @Modifying
    @Query(value = """
            UPDATE inventory SET available = available - :qty, version = version + 1
            WHERE sku = :sku AND available >= :qty
            """, nativeQuery = true)
    int decrementStock(String sku, int qty);
}
