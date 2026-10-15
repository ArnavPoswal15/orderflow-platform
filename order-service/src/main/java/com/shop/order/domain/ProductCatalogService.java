package com.shop.order.domain;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCatalogService {

    private final InventoryClient inventoryClient;
    private final StockCacheService stockCacheService;

    public ProductCatalogService(InventoryClient inventoryClient, StockCacheService stockCacheService) {
        this.inventoryClient = inventoryClient;
        this.stockCacheService = stockCacheService;
    }

    public List<ProductView> listProducts() {
        return inventoryClient.listAllProducts().stream()
                .map(this::toView)
                .toList();
    }

    private ProductView toView(InventoryClient.SkuInfo product) {
        Integer cached = stockCacheService.getAvailable(product.sku());
        int available;
        if (cached != null) {
            available = cached;
        } else {
            available = product.available();
            stockCacheService.cacheAvailable(product.sku(), available);
        }
        return new ProductView(product.sku(), product.name(), product.priceCents(), available);
    }

    public record ProductView(String sku, String name, long priceCents, int available) {}
}
