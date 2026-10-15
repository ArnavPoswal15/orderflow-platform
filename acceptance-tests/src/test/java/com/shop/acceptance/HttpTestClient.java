package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HttpTestClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public HttpTestClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public JsonNode createOrder(String sku, int qty, String paymentToken, String idempotencyKey) throws Exception {
        return createOrder(sku, qty, paymentToken, idempotencyKey, null);
    }

    public JsonNode createOrder(
            String sku, int qty, String paymentToken, String idempotencyKey, String xForwardedFor) throws Exception {
        RawOrderResponse raw = createOrderRaw(sku, qty, paymentToken, idempotencyKey, xForwardedFor);
        if (raw.statusCode() != 202 && raw.statusCode() != 200) {
            throw new IllegalStateException("create order failed: " + raw.statusCode() + " " + raw.body());
        }
        return mapper.readTree(raw.body());
    }

    public RawOrderResponse createOrderRaw(String sku, int qty, String paymentToken, String idempotencyKey)
            throws Exception {
        return createOrderRaw(sku, qty, paymentToken, idempotencyKey, null);
    }

    public RawOrderResponse createOrderRaw(
            String sku, int qty, String paymentToken, String idempotencyKey, String xForwardedFor)
            throws Exception {
        String body = String.format(
                "{\"items\":[{\"sku\":\"%s\",\"qty\":%d}],\"paymentToken\":\"%s\"}", sku, qty, paymentToken);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            builder.header("X-Forwarded-For", xForwardedFor);
        }
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, List<String>> headers = response.headers().map();
        return new RawOrderResponse(response.statusCode(), headers, response.body());
    }

    public record RawOrderResponse(int statusCode, Map<String, List<String>> headers, String body) {}

    public JsonNode flashSale(String sku, int concurrentOrders) throws Exception {
        String body = String.format("{\"sku\":\"%s\",\"concurrentOrders\":%d}", sku, concurrentOrders);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/flash-sale"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("flash sale failed: " + response.statusCode() + " " + response.body());
        }
        return mapper.readTree(response.body());
    }

    public JsonNode getOrder(UUID orderId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders/" + orderId))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    public int postShip(UUID orderId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders/" + orderId + "/ship"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    public int postCancel(UUID orderId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders/" + orderId + "/cancel"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"reason\":\"test cancel\"}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
