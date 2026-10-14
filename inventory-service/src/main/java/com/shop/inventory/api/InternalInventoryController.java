package com.shop.inventory.api;

import com.shop.inventory.persistence.InventoryEntity;
import com.shop.inventory.persistence.InventoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/internal")
public class InternalInventoryController {

    private final InventoryRepository inventoryRepository;

    public InternalInventoryController(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @GetMapping("/skus")
    public SkuBatchResponse list(@RequestParam("sku") String[] sku) {
        List<String> skus = Arrays.asList(sku);
        List<SkuInfo> result = inventoryRepository.findBySkuIn(skus).stream()
                .map(this::toInfo)
                .toList();
        return new SkuBatchResponse(result);
    }

    private SkuInfo toInfo(InventoryEntity e) {
        return new SkuInfo(e.getSku(), e.getName(), e.getPriceCents(), e.getAvailable());
    }

    public record SkuInfo(String sku, String name, long priceCents, int available) {}

    public record SkuBatchResponse(List<SkuInfo> skus) {}
}
