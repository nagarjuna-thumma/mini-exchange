package dev.nagarjuna.exchange.demo;

import dev.nagarjuna.exchange.engine.Depth;
import dev.nagarjuna.exchange.engine.OrderType;
import dev.nagarjuna.exchange.engine.Side;
import dev.nagarjuna.exchange.order.ExchangeService;
import dev.nagarjuna.exchange.order.OrderRepository;
import dev.nagarjuna.exchange.order.OrderStatus;
import dev.nagarjuna.exchange.order.PlaceOrderCommand;
import dev.nagarjuna.exchange.trader.Trader;
import dev.nagarjuna.exchange.trader.TraderRepository;
import dev.nagarjuna.exchange.trader.TraderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Only runs with the "demo" profile. Opens a few traders and runs two simple market-making
 * bots so the order book and trade tape move when you open the UI.
 */
@Component
@Profile("demo")
public class DemoMarket {

    private static final Logger log = LoggerFactory.getLogger(DemoMarket.class);
    private static final Map<String, BigDecimal> START_PRICES = Map.of(
            "NRTH", new BigDecimal("42.00"), "BRCH", new BigDecimal("18.50"), "LAKE", new BigDecimal("73.25"));

    private final TraderService traderService;
    private final TraderRepository traders;
    private final OrderRepository orders;
    private final ExchangeService exchange;
    private final Random random = new Random();
    private final AtomicBoolean ready = new AtomicBoolean();
    private List<Trader> bots = List.of();

    public DemoMarket(TraderService traderService, TraderRepository traders, OrderRepository orders, ExchangeService exchange) {
        this.traderService = traderService;
        this.traders = traders;
        this.orders = orders;
        this.exchange = exchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    void setUp() {
        if (!traders.existsByUsername("alice")) {
            for (String name : List.of("alice", "bob", "carol")) traderService.open(name, new BigDecimal("100000"), 1_000);
            for (String name : List.of("mm-north", "mm-south")) traderService.open(name, new BigDecimal("5000000"), 100_000);
            log.info("Demo traders opened");
        }
        bots = traders.findAllByOrderByUsername().stream().filter(t -> t.getUsername().startsWith("mm-")).toList();
        ready.set(true);
    }

    @Scheduled(fixedDelayString = "${exchange.bot.interval:600ms}")
    void tick() {
        if (!ready.get() || bots.isEmpty()) return;
        try {
            String symbol = List.copyOf(START_PRICES.keySet()).get(random.nextInt(START_PRICES.size()));
            Trader bot = bots.get(random.nextInt(bots.size()));
            trimOldOrders(bot, symbol);
            exchange.place(nextOrder(bot, symbol));
        } catch (RuntimeException e) {
            log.debug("Bot order failed: {}", e.getMessage());
        }
    }

    private PlaceOrderCommand nextOrder(Trader bot, String symbol) {
        Depth depth = exchange.depth(symbol);
        BigDecimal mid = mid(depth, symbol);
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        int quantity = (1 + random.nextInt(20)) * 10;

        if (random.nextInt(100) < 12) {
            return new PlaceOrderCommand(bot.getId(), symbol, side, OrderType.MARKET, null, quantity / 2 + 1);
        }
        // Quote a few cents either side of the mid, with a slow random drift.
        double offsetPercent = (random.nextGaussian() * 0.4) + (side == Side.BUY ? -0.25 : 0.25);
        BigDecimal price = mid.multiply(BigDecimal.valueOf(1 + offsetPercent / 100)).setScale(2, RoundingMode.HALF_UP);
        return new PlaceOrderCommand(bot.getId(), symbol, side, OrderType.LIMIT, price.max(new BigDecimal("0.01")), quantity);
    }

    private BigDecimal mid(Depth depth, String symbol) {
        if (!depth.bids().isEmpty() && !depth.asks().isEmpty()) {
            return depth.bids().get(0).price().add(depth.asks().get(0).price()).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
        }
        if (!depth.bids().isEmpty()) return depth.bids().get(0).price();
        if (!depth.asks().isEmpty()) return depth.asks().get(0).price();
        return START_PRICES.get(symbol);
    }

    /** Keeps each bot's book presence bounded by cancelling its oldest orders. */
    private void trimOldOrders(Trader bot, String symbol) {
        var open = orders.findByTraderIdAndStatusInOrderByCreatedAtDesc(bot.getId(), List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED))
                .stream().filter(o -> o.getSymbol().equals(symbol)).toList();
        for (int i = 25; i < open.size(); i++) {
            try {
                exchange.cancel(symbol, open.get(i).getId(), bot.getId());
            } catch (RuntimeException ignored) {
                // already filled in the meantime
            }
        }
    }
}
