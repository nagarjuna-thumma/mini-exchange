package dev.nagarjuna.exchange.order;

public class UnknownSymbolException extends RuntimeException {
    public UnknownSymbolException(String symbol) {
        super("Unknown symbol " + symbol);
    }
}
