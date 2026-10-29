package dev.nagarjuna.exchange.risk;

public enum RiskRule {
    /** Too many shares in one order. */
    MAX_QUANTITY,
    /** Order value (price x quantity) over the limit. */
    MAX_NOTIONAL,
    /** Limit price too far from the last trade. Catches typos like 4200 instead of 42.00. */
    PRICE_BAND,
    /** One trader sending orders faster than allowed. */
    ORDER_RATE
}
