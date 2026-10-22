package dev.nagarjuna.exchange.order;

import dev.nagarjuna.exchange.engine.OrderType;
import dev.nagarjuna.exchange.engine.Side;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceOrderCommand(UUID traderId, String symbol, Side side, OrderType type, BigDecimal price, int quantity) {

    public PlaceOrderCommand {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (type == OrderType.LIMIT) {
            if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Limit orders need a positive price");
            if (price.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Prices move in steps of 0.01");
        } else {
            price = null;
        }
    }
}
