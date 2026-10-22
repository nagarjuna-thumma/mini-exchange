package dev.nagarjuna.exchange.ledger;

public enum AccountKind {
    /** Belongs to a trader. Can never go below zero or below what open orders reserve. */
    TRADER,
    /** The outside world's side of cash deposits. Goes negative as cash comes in. */
    EXTERNAL,
    /** The issuing company's side of share deposits. */
    ISSUER
}
