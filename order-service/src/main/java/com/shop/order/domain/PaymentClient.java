package com.shop.order.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PaymentClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PaymentClient(@Value("${payment.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<JsonNode> getFailureRate() {
        return send(restClient.get().uri("/admin/failure-rate"));
    }

    public ResponseEntity<JsonNode> putFailureRate(JsonNode body) {
        return send(restClient.put()
                .uri("/admin/failure-rate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }

    private ResponseEntity<JsonNode> send(RestClient.RequestHeadersSpec<?> spec) {
        try {
            JsonNode body = spec.retrieve().body(JsonNode.class);
            return ResponseEntity.ok(body == null ? objectMapper.createObjectNode() : body);
        } catch (RestClientResponseException ex) {
            MediaType contentType = MediaType.APPLICATION_JSON;
            if (ex.getResponseHeaders() != null && ex.getResponseHeaders().getContentType() != null) {
                contentType = ex.getResponseHeaders().getContentType();
            } else if (ex.getStatusCode().is4xxClientError()) {
                contentType = MediaType.APPLICATION_PROBLEM_JSON;
            }
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(contentType)
                    .body(readTree(ex.getResponseBodyAsString()));
        }
    }

    private JsonNode readTree(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return objectMapper.createObjectNode().put("detail", raw);
        }
    }
}
