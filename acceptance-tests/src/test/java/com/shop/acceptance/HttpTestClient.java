package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

public class HttpTestClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public HttpTestClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public JsonNode createOrder(String sku, int qty, String paymentToken, String idempotencyKey) throws Exception {
        String body = String.format(
                "{\"items\":[{\"sku\":\"%s\",\"qty\":%d}],\"paymentToken\":\"%s\"}", sku, qty, paymentToken);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 202 && response.statusCode() != 200) {
            throw new IllegalStateException("create order failed: " + response.statusCode() + " " + response.body());
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
