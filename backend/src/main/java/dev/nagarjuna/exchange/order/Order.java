package dev.nagarjuna.exchange.order;

import dev.nagarjuna.exchange.engine.OrderType;
import dev.nagarjuna.exchange.engine.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    private Long seq;

    @Column(nullable = false)
    private UUID traderId;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    private BigDecimal limitPrice;

    private int quantity;

    private int filledQuantity;

    @Column(nullable = false)
    private BigDecimal filledNotional = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String rejectReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Order() {
    }

    private Order(PlaceOrderCommand cmd) {
        this.id = UUID.randomUUID();
        this.traderId = cmd.traderId();
        this.symbol = cmd.symbol();
        this.side = cmd.side();
        this.type = cmd.type();
        this.limitPrice = cmd.type() == OrderType.LIMIT ? cmd.price() : null;
        this.quantity = cmd.quantity();
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Order accepted(PlaceOrderCommand cmd, long seq) {
        Order order = new Order(cmd);
        order.seq = seq;
        order.status = OrderStatus.NEW;
        return order;
    }

    public static Order rejected(PlaceOrderCommand cmd, String reason) {
        Order order = new Order(cmd);
        order.status = OrderStatus.REJECTED;
        order.rejectReason = reason;
        return order;
    }

    public void recordFill(int fillQuantity, BigDecimal price) {
        if (!status.isOpen()) throw new IllegalStateException("Order " + id + " is " + status);
        filledQuantity += fillQuantity;
        filledNotional = filledNotional.add(price.multiply(BigDecimal.valueOf(fillQuantity)));
        status = filledQuantity == quantity ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        updatedAt = Instant.now();
    }

    public void cancel() {
        if (!status.isOpen()) throw new IllegalStateException("Order " + id + " is " + status);
        status = OrderStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    public int remaining() {
        return quantity - filledQuantity;
    }

    public BigDecimal averagePrice() {
        if (filledQuantity == 0) return null;
        return filledNotional.divide(BigDecimal.valueOf(filledQuantity), 4, RoundingMode.HALF_EVEN);
    }

    public BigDecimal getAveragePriceSafe() {
        return averagePrice();
    }

    public UUID getId() { return id; }
    public Long getSeq() { return seq; }
    public UUID getTraderId() { return traderId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public OrderType getType() { return type; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public int getQuantity() { return quantity; }
    public int getFilledQuantity() { return filledQuantity; }
    public OrderStatus getStatus() { return status; }
    public String getRejectReason() { return rejectReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
