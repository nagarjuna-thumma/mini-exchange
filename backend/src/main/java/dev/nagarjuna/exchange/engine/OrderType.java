package dev.nagarjuna.exchange.engine;

public enum OrderType {
    /** Rests on the book at its price until filled or cancelled. */
    LIMIT,
    /** Takes whatever liquidity is there at any price. Anything unfilled is cancelled, never rested. */
    MARKET
}
