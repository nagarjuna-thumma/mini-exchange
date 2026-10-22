package dev.nagarjuna.exchange.engine;

import java.util.List;

/**
 * What happened to an incoming order.
 *
 * @param fills             trades in the order they happened
 * @param rested            true if a limit order's remainder was added to the book
 * @param unfilledCancelled quantity dropped because a market order ran out of liquidity
 * @param selfTradeCancels  resting orders from the same trader that were cancelled instead of matched
 */
public record MatchResult(List<Fill> fills, boolean rested, int unfilledCancelled, List<CancelledOrder> selfTradeCancels) {

    public record CancelledOrder(BookOrder order, int cancelledQuantity) {
    }

    public int filledQuantity() {
        return fills.stream().mapToInt(Fill::quantity).sum();
    }
}
