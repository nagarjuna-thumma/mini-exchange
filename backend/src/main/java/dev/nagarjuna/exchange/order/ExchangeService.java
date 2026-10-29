package dev.nagarjuna.exchange.order;

import dev.nagarjuna.exchange.engine.Depth;
import dev.nagarjuna.exchange.ledger.InsufficientFundsException;
import dev.nagarjuna.exchange.market.MarketDataHub;
import dev.nagarjuna.exchange.risk.PreTradeRisk;
import dev.nagarjuna.exchange.risk.RiskRejection;
import dev.nagarjuna.exchange.trader.InstrumentRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExchangeService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeService.class);

    private final OrderProcessor processor;
    private final InstrumentRepository instruments;
    private final TradeRepository trades;
    private final MarketDataHub marketData;
    private final PreTradeRisk risk;
    private final int depthLevels;
    private final Map<String, SymbolWorker> workers = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();

    public ExchangeService(OrderProcessor processor, InstrumentRepository instruments, TradeRepository trades,
                           MarketDataHub marketData, PreTradeRisk risk,
                           @Value("${exchange.depth-levels:10}") int depthLevels) {
        this.processor = processor;
        this.instruments = instruments;
        this.trades = trades;
        this.marketData = marketData;
        this.risk = risk;
        this.depthLevels = depthLevels;
    }

    @PostConstruct
    void start() {
        instruments.findAll().forEach(instrument -> {
            String symbol = instrument.getSymbol();
            SymbolWorker worker = new SymbolWorker(symbol, processor.rebuild(symbol));
            workers.put(symbol, worker);
            trades.findFirstBySymbolOrderByIdDesc(symbol).ifPresent(t -> lastPrices.put(symbol, t.getPrice()));
            log.info("Order book for {} rebuilt with {} resting orders", symbol, worker.run(() -> worker.book().size()));
        });
    }

    public PlaceOutcome place(PlaceOrderCommand cmd) {
        SymbolWorker worker = worker(cmd.symbol());
        return worker.run(() -> {
            Optional<RiskRejection> blocked = risk.check(cmd, lastPrices.get(cmd.symbol()));
            if (blocked.isPresent()) {
                log.info("Order from trader {} on {} blocked: {}", cmd.traderId(), cmd.symbol(), blocked.get().reason());
                return processor.reject(cmd, blocked.get().reason());
            }
            PlaceOutcome outcome;
            try {
                outcome = processor.place(worker.book(), cmd);
            } catch (InsufficientFundsException e) {
                // Funds were spent by another symbol between the precheck and the lock.
                worker.replaceBook(processor.rebuild(cmd.symbol()));
                outcome = processor.reject(cmd, e.getMessage());
            } catch (RuntimeException e) {
                worker.replaceBook(processor.rebuild(cmd.symbol()));
                throw e;
            }
            if (!outcome.trades().isEmpty()) {
                lastPrices.put(cmd.symbol(), outcome.trades().get(outcome.trades().size() - 1).getPrice());
            }
            marketData.publish(cmd.symbol(), worker.book().depth(depthLevels), outcome.trades());
            return outcome;
        });
    }

    public Order cancel(String symbol, UUID orderId, UUID traderId) {
        SymbolWorker worker = worker(symbol);
        return worker.run(() -> {
            Order order;
            try {
                order = processor.cancel(worker.book(), orderId, traderId);
            } catch (OrderNotFoundException | OrderNotOpenException e) {
                throw e; // nothing in the book changed
            } catch (RuntimeException e) {
                worker.replaceBook(processor.rebuild(symbol));
                throw e;
            }
            marketData.publish(symbol, worker.book().depth(depthLevels), List.of());
            return order;
        });
    }

    public Depth depth(String symbol) {
        SymbolWorker worker = worker(symbol);
        return worker.run(() -> worker.book().depth(depthLevels));
    }

    private SymbolWorker worker(String symbol) {
        SymbolWorker worker = workers.get(symbol);
        if (worker == null) throw new UnknownSymbolException(symbol);
        return worker;
    }

    @PreDestroy
    void stop() throws InterruptedException {
        for (SymbolWorker worker : workers.values()) worker.shutdown();
    }
}
