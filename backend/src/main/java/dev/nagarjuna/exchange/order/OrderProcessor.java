package dev.nagarjuna.exchange.order;

import dev.nagarjuna.exchange.engine.BookOrder;
import dev.nagarjuna.exchange.engine.Fill;
import dev.nagarjuna.exchange.engine.MatchResult;
import dev.nagarjuna.exchange.engine.OrderBook;
import dev.nagarjuna.exchange.engine.OrderType;
import dev.nagarjuna.exchange.engine.Side;
import dev.nagarjuna.exchange.ledger.Account;
import dev.nagarjuna.exchange.ledger.AccountLocks;
import dev.nagarjuna.exchange.ledger.AccountRepository;
import dev.nagarjuna.exchange.ledger.InsufficientFundsException;
import dev.nagarjuna.exchange.ledger.Ledger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Applies the engine's decisions to the database: orders, trades, holds and settlement, all in
 * one transaction. Always called from the symbol's own thread, so the order book can't change
 * underneath it.
 *
 * If anything here throws after the book was changed, the transaction rolls back and the caller
 * rebuilds the book from the database. Memory never gets ahead of what is stored.
 */
@Service
public class OrderProcessor {

    static final String CASH = "CAD";

    private final OrderRepository orders;
    private final TradeRepository trades;
    private final AccountRepository accounts;
    private final AccountLocks locks;
    private final Ledger ledger;

    public OrderProcessor(OrderRepository orders, TradeRepository trades, AccountRepository accounts,
                          AccountLocks locks, Ledger ledger) {
        this.orders = orders;
        this.trades = trades;
        this.accounts = accounts;
        this.locks = locks;
        this.ledger = ledger;
    }

    @Transactional
    public PlaceOutcome place(OrderBook book, PlaceOrderCommand cmd) {
        // Cheap check without locks, so an obviously unaffordable order never touches the book.
        // The real check happens again below, under row locks.
        String shortfall = precheck(book, cmd);
        if (shortfall != null) return new PlaceOutcome(orders.save(Order.rejected(cmd, shortfall)), List.of());

        Order order = orders.save(Order.accepted(cmd, orders.nextSeq()));
        BookOrder incoming = new BookOrder(order.getId(), cmd.traderId(), cmd.side(), cmd.type(), cmd.price(), cmd.quantity());
        MatchResult result = book.match(incoming);

        Map<UUID, Account> locked = lockAccounts(cmd.symbol(), tradersInvolved(cmd.traderId(), result));
        Account buyerCashOfIncoming = account(locked, cmd.traderId(), CASH);
        Account sharesOfIncoming = account(locked, cmd.traderId(), cmd.symbol());

        // Reserve what the order could spend. Throws if funds were spent by another symbol meanwhile.
        if (cmd.side() == Side.SELL) {
            sharesOfIncoming.hold(BigDecimal.valueOf(cmd.quantity()));
        } else if (cmd.type() == OrderType.LIMIT) {
            buyerCashOfIncoming.hold(cmd.price().multiply(BigDecimal.valueOf(cmd.quantity())));
        } else {
            BigDecimal cost = result.fills().stream().map(Fill::notional).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (buyerCashOfIncoming.available().compareTo(cost) < 0) {
                throw new InsufficientFundsException(CASH, cost, buyerCashOfIncoming.available());
            }
        }

        List<Trade> newTrades = new ArrayList<>();
        for (Fill fill : result.fills()) {
            newTrades.add(settle(cmd, order, fill, locked));
        }

        for (MatchResult.CancelledOrder cancelled : result.selfTradeCancels()) {
            Order resting = orders.findById(cancelled.order().id()).orElseThrow();
            resting.cancel();
            releaseHold(locked, resting.getTraderId(), cmd.symbol(), resting.getSide(), resting.getLimitPrice(),
                    cancelled.cancelledQuantity());
        }

        if (result.unfilledCancelled() > 0) {
            // Market order ran out of liquidity. The unfilled part is cancelled, never rested.
            if (cmd.side() == Side.SELL) sharesOfIncoming.release(BigDecimal.valueOf(result.unfilledCancelled()));
            order.cancel();
        }
        return new PlaceOutcome(order, newTrades);
    }

    /** Saves a rejected order after a failed attempt was rolled back. */
    @Transactional
    public PlaceOutcome reject(PlaceOrderCommand cmd, String reason) {
        return new PlaceOutcome(orders.save(Order.rejected(cmd, reason)), List.of());
    }

    @Transactional
    public Order cancel(OrderBook book, UUID orderId, UUID traderId) {
        Order order = orders.findById(orderId)
                .filter(o -> o.getTraderId().equals(traderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getStatus().isOpen()) throw new OrderNotOpenException(order);

        BookOrder removed = book.cancel(orderId)
                .orElseThrow(() -> new IllegalStateException("Open order " + orderId + " missing from the book"));
        Map<UUID, Account> locked = lockAccounts(order.getSymbol(), Set.of(traderId));
        releaseHold(locked, traderId, order.getSymbol(), order.getSide(), order.getLimitPrice(), removed.remaining());
        order.cancel();
        return order;
    }

    /** Rebuilds a symbol's book from the open orders in the database, in their original time order. */
    @Transactional(readOnly = true)
    public OrderBook rebuild(String symbol) {
        OrderBook book = new OrderBook(symbol);
        for (Order o : orders.findBySymbolAndStatusInOrderBySeq(symbol, List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED))) {
            book.restore(new BookOrder(o.getId(), o.getTraderId(), o.getSide(), OrderType.LIMIT, o.getLimitPrice(), o.remaining()));
        }
        return book;
    }

    private Trade settle(PlaceOrderCommand cmd, Order incomingOrder, Fill fill, Map<UUID, Account> locked) {
        String symbol = cmd.symbol();
        BigDecimal quantity = BigDecimal.valueOf(fill.quantity());
        BigDecimal notional = fill.notional();

        Account buyerCash = account(locked, fill.buyerId(), CASH);
        Account buyerShares = account(locked, fill.buyerId(), symbol);
        Account sellerCash = account(locked, fill.sellerId(), CASH);
        Account sellerShares = account(locked, fill.sellerId(), symbol);

        // Release the reservations this fill uses up. A buy limit reserved its own limit price,
        // which can be higher than the trade price; the difference simply becomes available again.
        sellerShares.release(quantity);
        BigDecimal buyerReservedPrice = fill.aggressorSide() == Side.BUY ? cmd.price() : fill.price();
        if (buyerReservedPrice != null) buyerCash.release(buyerReservedPrice.multiply(quantity));

        UUID txId = ledger.post("TRADE", "%s %d @ %s".formatted(symbol, fill.quantity(), fill.price().toPlainString()), List.of(
                new Ledger.Leg(buyerCash, notional.negate()),
                new Ledger.Leg(sellerCash, notional),
                new Ledger.Leg(sellerShares, quantity.negate()),
                new Ledger.Leg(buyerShares, quantity)));

        incomingOrder.recordFill(fill.quantity(), fill.price());
        orders.findById(fill.restingOrderId()).orElseThrow().recordFill(fill.quantity(), fill.price());
        return trades.save(new Trade(symbol, fill, txId));
    }

    private void releaseHold(Map<UUID, Account> locked, UUID traderId, String symbol, Side side, BigDecimal limitPrice, int quantity) {
        if (side == Side.SELL) {
            account(locked, traderId, symbol).release(BigDecimal.valueOf(quantity));
        } else {
            account(locked, traderId, CASH).release(limitPrice.multiply(BigDecimal.valueOf(quantity)));
        }
    }

    private String precheck(OrderBook book, PlaceOrderCommand cmd) {
        if (cmd.side() == Side.SELL) {
            BigDecimal available = availableOf(cmd.traderId(), cmd.symbol());
            return available.compareTo(BigDecimal.valueOf(cmd.quantity())) < 0
                    ? "Not enough %s shares: %s available".formatted(cmd.symbol(), available.toPlainString()) : null;
        }
        BigDecimal cost = cmd.type() == OrderType.LIMIT
                ? cmd.price().multiply(BigDecimal.valueOf(cmd.quantity()))
                : book.costToBuy(cmd.traderId(), cmd.quantity());
        if (cmd.type() == OrderType.MARKET && cost.signum() == 0) return "No sellers in the book";
        BigDecimal available = availableOf(cmd.traderId(), CASH);
        return available.compareTo(cost) < 0
                ? "Not enough cash: needs %s, %s available".formatted(cost.toPlainString(), available.toPlainString()) : null;
    }

    private BigDecimal availableOf(UUID traderId, String asset) {
        return accounts.findByOwnerIdAndAsset(traderId, asset).map(Account::available)
                .orElseThrow(() -> new IllegalArgumentException("Unknown trader " + traderId));
    }

    private Set<UUID> tradersInvolved(UUID incomingTrader, MatchResult result) {
        Set<UUID> traders = new HashSet<>();
        traders.add(incomingTrader);
        result.fills().forEach(f -> traders.add(f.restingTraderId()));
        return traders;
    }

    private Map<UUID, Account> lockAccounts(String symbol, Set<UUID> traders) {
        List<UUID> ids = accounts.findByOwnerIdInAndAssetIn(traders, List.of(CASH, symbol)).stream().map(Account::getId).toList();
        Map<UUID, Account> byId = new HashMap<>();
        locks.lockInOrder(ids).forEach(a -> byId.put(a.getId(), a));
        return byId;
    }

    private static Account account(Map<UUID, Account> locked, UUID traderId, String asset) {
        return locked.values().stream()
                .filter(a -> traderId.equals(a.getOwnerId()) && a.getAsset().equals(asset))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No locked " + asset + " account for trader " + traderId));
    }
}
