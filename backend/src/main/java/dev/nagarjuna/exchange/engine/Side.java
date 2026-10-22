package dev.nagarjuna.exchange.engine;

public enum Side {
    BUY, SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
