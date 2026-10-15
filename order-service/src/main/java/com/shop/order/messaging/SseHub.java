package com.shop.order.messaging;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final List<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<UUID, List<SseEmitter>> orderEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribeGlobal() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        globalEmitters.add(emitter);
        registerCallbacks(emitter, () -> globalEmitters.remove(emitter));
        return emitter;
    }

    public SseEmitter subscribeOrder(UUID orderId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        orderEmitters.computeIfAbsent(orderId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        registerCallbacks(emitter, () -> {
            List<SseEmitter> list = orderEmitters.get(orderId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    orderEmitters.remove(orderId, list);
                }
            }
        });
        return emitter;
    }

    public void send(EventEnvelope envelope) {
        String data = EventJson.serialize(envelope);
        broadcast(globalEmitters, envelope.type(), data);
        List<SseEmitter> orderSubs = orderEmitters.get(envelope.orderId());
        if (orderSubs != null) {
            broadcast(orderSubs, envelope.type(), data);
        }
    }

    private static void broadcast(List<SseEmitter> emitters, String eventName, String data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.debug("SSE client disconnected", e);
                emitter.completeWithError(e);
            }
        }
    }

    private static void registerCallbacks(SseEmitter emitter, Runnable onComplete) {
        emitter.onCompletion(onComplete);
        emitter.onTimeout(() -> {
            onComplete.run();
            emitter.complete();
        });
        emitter.onError(ex -> onComplete.run());
    }
}
