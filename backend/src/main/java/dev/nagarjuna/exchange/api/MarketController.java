package dev.nagarjuna.exchange.api;

import dev.nagarjuna.exchange.engine.Depth;
import dev.nagarjuna.exchange.market.MarketDataHub;
import dev.nagarjuna.exchange.market.TradeView;
import dev.nagarjuna.exchange.order.ExchangeService;
import dev.nagarjuna.exchange.order.TradeRepository;
import dev.nagarjuna.exchange.trader.InstrumentRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MarketController {

    private final ExchangeService exchange;
    private final MarketDataHub marketData;
    private final TradeRepository trades;
    private final InstrumentRepository instruments;

    public MarketController(ExchangeService exchange, MarketDataHub marketData, TradeRepository trades,
                            InstrumentRepository instruments) {
        this.exchange = exchange;
        this.marketData = marketData;
        this.trades = trades;
        this.instruments = instruments;
    }

    @GetMapping("/instruments")
    public List<Views.InstrumentView> instruments() {
        return instruments.findAllByOrderBySymbol().stream()
                .map(i -> new Views.InstrumentView(i.getSymbol(), i.getName(), i.getCurrency(),
                        trades.findFirstBySymbolOrderByIdDesc(i.getSymbol()).map(t -> t.getPrice()).orElse(null)))
                .toList();
    }

    @GetMapping("/market/{symbol}/depth")
    public Depth depth(@PathVariable String symbol) {
        return exchange.depth(symbol);
    }

    @GetMapping("/market/{symbol}/trades")
    public List<TradeView> recentTrades(@PathVariable String symbol) {
        exchange.depth(symbol); // 404s for unknown symbols
        return trades.findTop100BySymbolOrderByIdDesc(symbol).stream().map(TradeView::from).toList();
    }

    /** Live depth and trades. Sends a depth snapshot first, then every change. */
    @GetMapping(value = "/market/{symbol}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String symbol) {
        return marketData.subscribe(symbol, exchange.depth(symbol));
    }
}
