package dev.nagarjuna.exchange.engine;

import java.math.BigDecimal;
import java.util.List;

/** Aggregated view of the book: best prices first on both sides. */
public record Depth(String symbol, List<Level> bids, List<Level> asks) {

    public record Level(BigDecimal price, int quantity, int orders) {
    }
}
