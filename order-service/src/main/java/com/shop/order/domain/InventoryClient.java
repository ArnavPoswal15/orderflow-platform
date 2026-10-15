package com.shop.order.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(@Value("${inventory.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<SkuInfo> listAllProducts() {
        ProductListResponse response = restClient.get()
                .uri("/internal/products")
                .retrieve()
                .body(ProductListResponse.class);
        if (response == null || response.products == null) {
            return List.of();
        }
        return response.products;
    }

    public Map<String, SkuInfo> fetchSkus(List<String> skus) {
        SkuBatchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/skus").queryParam("sku", skus.toArray()).build())
                .retrieve()
                .body(SkuBatchResponse.class);
        if (response == null || response.skus == null) {
            return Map.of();
        }
        return response.skus.stream().collect(Collectors.toMap(SkuInfo::sku, s -> s));
    }

    public record SkuInfo(String sku, String name, long priceCents, int available) {}

    private record SkuBatchResponse(List<SkuInfo> skus) {}

    private record ProductListResponse(List<SkuInfo> products) {}
}
