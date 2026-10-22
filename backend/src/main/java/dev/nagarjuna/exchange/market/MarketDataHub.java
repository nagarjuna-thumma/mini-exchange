package dev.nagarjuna.exchange.market;

import dev.nagarjuna.exchange.engine.Depth;
import dev.nagarjuna.exchange.order.Trade;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes order book and trade updates to browsers over Server-Sent Events, one stream per symbol.
 * Updates go out only after the transaction that caused them has committed.
 */
@Component
public class MarketDataHub {

    private final Map<String, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String symbol, Depth snapshot) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> set = subscribers.computeIfAbsent(symbol, s -> ConcurrentHashMap.newKeySet());
        set.add(emitter);
        emitter.onCompletion(() -> set.remove(emitter));
        emitter.onTimeout(() -> set.remove(emitter));
        emitter.onError(e -> set.remove(emitter));
        send(set, emitter, "depth", snapshot);
        return emitter;
    }

    public void publish(String symbol, Depth depth, List<Trade> trades) {
        Set<SseEmitter> set = subscribers.get(symbol);
        if (set == null || set.isEmpty()) return;
        List<TradeView> views = trades.stream().map(TradeView::from).toList();
        for (SseEmitter emitter : set) {
            if (!views.isEmpty()) send(set, emitter, "trades", views);
            send(set, emitter, "depth", depth);
        }
    }

    private static void send(Set<SseEmitter> set, SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            set.remove(emitter);
        }
    }
}
