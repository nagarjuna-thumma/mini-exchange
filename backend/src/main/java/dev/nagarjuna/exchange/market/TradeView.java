package dev.nagarjuna.exchange.market;

import dev.nagarjuna.exchange.order.Trade;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeView(long id, String symbol, BigDecimal price, int quantity, String aggressorSide, Instant executedAt) {

    public static TradeView from(Trade t) {
        return new TradeView(t.getId(), t.getSymbol(), t.getPrice(), t.getQuantity(), t.getAggressorSide().name(), t.getExecutedAt());
    }
}
