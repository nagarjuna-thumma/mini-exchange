package dev.nagarjuna.exchange.order;

import dev.nagarjuna.exchange.engine.Fill;
import dev.nagarjuna.exchange.engine.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private BigDecimal price;

    private int quantity;

    @Column(nullable = false)
    private UUID buyOrderId;

    @Column(nullable = false)
    private UUID sellOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side aggressorSide;

    @Column(nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private Instant executedAt;

    protected Trade() {
    }

    public Trade(String symbol, Fill fill, UUID transactionId) {
        this.symbol = symbol;
        this.price = fill.price();
        this.quantity = fill.quantity();
        this.buyOrderId = fill.buyOrderId();
        this.sellOrderId = fill.sellOrderId();
        this.aggressorSide = fill.aggressorSide();
        this.transactionId = transactionId;
        this.executedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public Side getAggressorSide() { return aggressorSide; }
    public Instant getExecutedAt() { return executedAt; }
}
