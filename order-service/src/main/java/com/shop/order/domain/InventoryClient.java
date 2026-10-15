package com.shop.order.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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

    public void setAvailable(String sku, int available) {
        try {
            restClient.put()
                    .uri("/internal/skus/{sku}/stock", sku)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StockUpdate(available))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new UnknownSkuException(sku);
            }
            throw ex;
        }
    }

    public record SkuInfo(String sku, String name, long priceCents, int available) {}

    public static class UnknownSkuException extends RuntimeException {
        public UnknownSkuException(String sku) {
            super("Unknown SKU: " + sku);
        }
    }

    private record StockUpdate(int available) {}

    private record SkuBatchResponse(List<SkuInfo> skus) {}

    private record ProductListResponse(List<SkuInfo> products) {}
}
