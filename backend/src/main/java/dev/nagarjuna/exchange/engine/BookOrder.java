package dev.nagarjuna.exchange.engine;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An order as the matching engine sees it. Only remaining quantity changes; everything else is fixed.
 * Price is null for market orders.
 */
public final class BookOrder {

    private final UUID id;
    private final UUID traderId;
    private final Side side;
    private final OrderType type;
    private final BigDecimal price;
    private int remaining;

    public BookOrder(UUID id, UUID traderId, Side side, OrderType type, BigDecimal price, int remaining) {
        if (remaining <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (type == OrderType.LIMIT && (price == null || price.signum() <= 0)) {
            throw new IllegalArgumentException("Limit orders need a positive price");
        }
        this.id = id;
        this.traderId = traderId;
        this.side = side;
        this.type = type;
        this.price = type == OrderType.MARKET ? null : price;
        this.remaining = remaining;
    }

    public static BookOrder limit(UUID traderId, Side side, String price, int quantity) {
        return new BookOrder(UUID.randomUUID(), traderId, side, OrderType.LIMIT, new BigDecimal(price), quantity);
    }

    public static BookOrder market(UUID traderId, Side side, int quantity) {
        return new BookOrder(UUID.randomUUID(), traderId, side, OrderType.MARKET, null, quantity);
    }

    void fill(int quantity) {
        if (quantity <= 0 || quantity > remaining) throw new IllegalStateException("Bad fill of " + quantity);
        remaining -= quantity;
    }

    /** Would this incoming order trade against a resting order at the given price? */
    boolean acceptsPrice(BigDecimal restingPrice) {
        if (type == OrderType.MARKET) return true;
        return side == Side.BUY ? price.compareTo(restingPrice) >= 0 : price.compareTo(restingPrice) <= 0;
    }

    public UUID id() { return id; }
    public UUID traderId() { return traderId; }
    public Side side() { return side; }
    public OrderType type() { return type; }
    public BigDecimal price() { return price; }
    public int remaining() { return remaining; }
    public boolean isFilled() { return remaining == 0; }
}
