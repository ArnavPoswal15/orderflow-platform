package com.shop.inventory.api;

import com.shop.inventory.domain.StockAdminService;
import com.shop.inventory.persistence.InventoryEntity;
import com.shop.inventory.persistence.InventoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/internal")
public class InternalInventoryController {

    private final InventoryRepository inventoryRepository;
    private final StockAdminService stockAdminService;

    public InternalInventoryController(InventoryRepository inventoryRepository, StockAdminService stockAdminService) {
        this.inventoryRepository = inventoryRepository;
        this.stockAdminService = stockAdminService;
    }

    @GetMapping("/skus")
    public SkuBatchResponse list(@RequestParam("sku") String[] sku) {
        List<String> skus = Arrays.asList(sku);
        List<SkuInfo> result = inventoryRepository.findBySkuIn(skus).stream()
                .map(this::toInfo)
                .toList();
        return new SkuBatchResponse(result);
    }

    @PutMapping("/skus/{sku}/stock")
    public SkuInfo setStock(@PathVariable String sku, @RequestBody SetStockRequest body) {
        return toInfo(stockAdminService.setAvailable(sku, body.available()));
    }

    @GetMapping("/products")
    public ProductListResponse allProducts() {
        List<SkuInfo> result = inventoryRepository.findAll().stream()
                .map(this::toInfo)
                .toList();
        return new ProductListResponse(result);
    }

    private SkuInfo toInfo(InventoryEntity e) {
        return new SkuInfo(e.getSku(), e.getName(), e.getPriceCents(), e.getAvailable());
    }

    public record SetStockRequest(int available) {}

    public record SkuInfo(String sku, String name, long priceCents, int available) {}

    public record SkuBatchResponse(List<SkuInfo> skus) {}

    public record ProductListResponse(List<SkuInfo> products) {}
}
