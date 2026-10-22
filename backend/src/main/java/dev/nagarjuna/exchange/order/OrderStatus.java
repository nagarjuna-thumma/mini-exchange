package dev.nagarjuna.exchange.order;

public enum OrderStatus {
    NEW, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED;

    public boolean isOpen() {
        return this == NEW || this == PARTIALLY_FILLED;
    }
}
