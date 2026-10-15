package com.shop.inventory.domain;

import com.shop.inventory.persistence.InventoryEntity;
import com.shop.inventory.persistence.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAdminService {

    private final InventoryRepository inventoryRepository;

    public StockAdminService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public InventoryEntity setAvailable(String sku, int available) {
        int updated = inventoryRepository.setAvailable(sku, available);
        if (updated == 0) {
            throw new UnknownSkuException(sku);
        }
        return inventoryRepository.findById(sku).orElseThrow(() -> new UnknownSkuException(sku));
    }

    public static class UnknownSkuException extends RuntimeException {
        public UnknownSkuException(String sku) {
            super("Unknown SKU: " + sku);
        }
    }
}
