package dev.nagarjuna.exchange.engine;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A price-time priority limit order book for one symbol.
 *
 * Bids are sorted highest first and asks lowest first. Within a price level, orders queue
 * in arrival order. An incoming order matches against the best opposite level until it is
 * filled, the price no longer works for it, or the book runs dry.
 *
 * Not thread-safe on purpose: each symbol's book is owned by a single thread (see
 * SymbolWorker), which keeps matching deterministic and free of locks.
 */
public final class OrderBook {

    private final String symbol;
    private final NavigableMap<BigDecimal, Deque<BookOrder>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, Deque<BookOrder>> asks = new TreeMap<>();
    private final Map<UUID, BookOrder> byId = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public MatchResult match(BookOrder incoming) {
        if (byId.containsKey(incoming.id())) throw new IllegalArgumentException("Duplicate order " + incoming.id());

        NavigableMap<BigDecimal, Deque<BookOrder>> opposite = incoming.side() == Side.BUY ? asks : bids;
        List<Fill> fills = new ArrayList<>();
        List<MatchResult.CancelledOrder> selfTradeCancels = new ArrayList<>();

        while (!incoming.isFilled() && !opposite.isEmpty()) {
            Map.Entry<BigDecimal, Deque<BookOrder>> best = opposite.firstEntry();
            if (!incoming.acceptsPrice(best.getKey())) break;

            Deque<BookOrder> level = best.getValue();
            BookOrder resting = level.peekFirst();

            if (resting.traderId().equals(incoming.traderId())) {
                // Self-trade prevention: a trader can't trade with themselves (that would be wash
                // trading). The older resting order is cancelled and matching carries on.
                level.pollFirst();
                byId.remove(resting.id());
                selfTradeCancels.add(new MatchResult.CancelledOrder(resting, resting.remaining()));
            } else {
                int quantity = Math.min(incoming.remaining(), resting.remaining());
                incoming.fill(quantity);
                resting.fill(quantity);
                fills.add(new Fill(incoming.id(), incoming.traderId(), resting.id(), resting.traderId(),
                        incoming.side(), best.getKey(), quantity));
                if (resting.isFilled()) {
                    level.pollFirst();
                    byId.remove(resting.id());
                }
            }
            if (level.isEmpty()) opposite.pollFirstEntry();
        }

        boolean rested = false;
        int unfilledCancelled = 0;
        if (!incoming.isFilled()) {
            if (incoming.type() == OrderType.LIMIT) {
                rest(incoming);
                rested = true;
            } else {
                unfilledCancelled = incoming.remaining();
            }
        }
        return new MatchResult(List.copyOf(fills), rested, unfilledCancelled, List.copyOf(selfTradeCancels));
    }

    /** Puts an order straight on the book without matching. Used when rebuilding from the database. */
    public void restore(BookOrder order) {
        if (order.type() != OrderType.LIMIT) throw new IllegalArgumentException("Only limit orders rest on the book");
        BigDecimal bestOpposite = order.side() == Side.BUY ? bestAsk().orElse(null) : bestBid().orElse(null);
        if (bestOpposite != null && order.acceptsPrice(bestOpposite)) {
            throw new IllegalStateException("Restoring " + order.id() + " would cross the book");
        }
        rest(order);
    }

    public Optional<BookOrder> cancel(UUID orderId) {
        BookOrder order = byId.remove(orderId);
        if (order == null) return Optional.empty();
        NavigableMap<BigDecimal, Deque<BookOrder>> side = order.side() == Side.BUY ? bids : asks;
        Deque<BookOrder> level = side.get(order.price());
        level.remove(order);
        if (level.isEmpty()) side.remove(order.price());
        return Optional.of(order);
    }

    /**
     * What it would cost to buy this quantity right now, walking up the asks. Ignores the buyer's
     * own asks, which self-trade prevention would cancel rather than fill. Used to check a market
     * buy is affordable before running it.
     */
    public BigDecimal costToBuy(UUID buyerId, int quantity) {
        BigDecimal cost = BigDecimal.ZERO;
        int needed = quantity;
        for (Map.Entry<BigDecimal, Deque<BookOrder>> level : asks.entrySet()) {
            for (BookOrder order : level.getValue()) {
                if (order.traderId().equals(buyerId)) continue;
                int take = Math.min(needed, order.remaining());
                cost = cost.add(level.getKey().multiply(BigDecimal.valueOf(take)));
                needed -= take;
                if (needed == 0) return cost;
            }
        }
        return cost;
    }

    public Optional<BigDecimal> bestBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey());
    }

    public Optional<BigDecimal> bestAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey());
    }

    public boolean contains(UUID orderId) {
        return byId.containsKey(orderId);
    }

    public int size() {
        return byId.size();
    }

    public Depth depth(int levels) {
        return new Depth(symbol, aggregate(bids, levels), aggregate(asks, levels));
    }

    /** Every resting order, best price first, then time. Handy for tests and recovery checks. */
    public List<BookOrder> restingOrders(Side side) {
        List<BookOrder> result = new ArrayList<>();
        (side == Side.BUY ? bids : asks).values().forEach(result::addAll);
        return Collections.unmodifiableList(result);
    }

    private void rest(BookOrder order) {
        NavigableMap<BigDecimal, Deque<BookOrder>> side = order.side() == Side.BUY ? bids : asks;
        side.computeIfAbsent(order.price(), p -> new ArrayDeque<>()).addLast(order);
        byId.put(order.id(), order);
    }

    private static List<Depth.Level> aggregate(NavigableMap<BigDecimal, Deque<BookOrder>> side, int levels) {
        List<Depth.Level> result = new ArrayList<>();
        Iterator<Map.Entry<BigDecimal, Deque<BookOrder>>> it = side.entrySet().iterator();
        while (it.hasNext() && result.size() < levels) {
            Map.Entry<BigDecimal, Deque<BookOrder>> entry = it.next();
            int quantity = entry.getValue().stream().mapToInt(BookOrder::remaining).sum();
            result.add(new Depth.Level(entry.getKey(), quantity, entry.getValue().size()));
        }
        return result;
    }
}
