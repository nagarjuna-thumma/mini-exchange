package dev.nagarjuna.exchange.engine;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One match between the incoming (aggressor) order and one resting order.
 * The price is always the resting order's price: whoever was there first sets it.
 */
public record Fill(
        UUID incomingOrderId,
        UUID incomingTraderId,
        UUID restingOrderId,
        UUID restingTraderId,
        Side aggressorSide,
        BigDecimal price,
        int quantity) {

    public UUID buyOrderId() { return aggressorSide == Side.BUY ? incomingOrderId : restingOrderId; }
    public UUID sellOrderId() { return aggressorSide == Side.SELL ? incomingOrderId : restingOrderId; }
    public UUID buyerId() { return aggressorSide == Side.BUY ? incomingTraderId : restingTraderId; }
    public UUID sellerId() { return aggressorSide == Side.SELL ? incomingTraderId : restingTraderId; }

    public BigDecimal notional() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
