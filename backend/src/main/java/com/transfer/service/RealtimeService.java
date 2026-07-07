package com.transfer.service;

import com.transfer.dto.RealtimeEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RealtimeService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        sendToEmitter(emitter, new RealtimeEvent("CONNECTED", LocalDateTime.now(), Map.of("message", "stream opened")));
        return emitter;
    }

    public void publish(String type, Map<String, Object> data) {
        RealtimeEvent event = new RealtimeEvent(type, LocalDateTime.now(), data);
        Iterator<SseEmitter> iterator = emitters.iterator();
        while (iterator.hasNext()) {
            SseEmitter emitter = iterator.next();
            if (!sendToEmitter(emitter, event)) {
                emitters.remove(emitter);
            }
        }
    }

    private boolean sendToEmitter(SseEmitter emitter, RealtimeEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event));
            return true;
        } catch (IOException | IllegalStateException ex) {
            return false;
        }
    }
}
