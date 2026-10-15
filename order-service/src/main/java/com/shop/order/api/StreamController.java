package com.shop.order.api;

import com.shop.order.messaging.SseHub;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final SseHub sseHub;

    public StreamController(SseHub sseHub) {
        this.sseHub = sseHub;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter allEvents() {
        return sseHub.subscribeGlobal();
    }

    @GetMapping(value = "/orders/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter orderEvents(@PathVariable UUID id) {
        return sseHub.subscribeOrder(id);
    }
}
