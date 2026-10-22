package dev.nagarjuna.exchange.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    private UUID ownerId;

    @Column(nullable = false)
    private String asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountKind kind;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal held = BigDecimal.ZERO;

    @Version
    private long version;

    protected Account() {
    }

    public static Account forTrader(UUID traderId, String asset) {
        Account account = new Account();
        account.id = UUID.randomUUID();
        account.ownerId = traderId;
        account.asset = asset;
        account.kind = AccountKind.TRADER;
        return account;
    }

    public BigDecimal available() {
        return balance.subtract(held);
    }

    void apply(BigDecimal amount) {
        balance = balance.add(amount);
    }

    /** Reserve funds for an open order. Callers must hold the row lock. */
    public void hold(BigDecimal amount) {
        if (kind == AccountKind.TRADER && available().compareTo(amount) < 0) {
            throw new InsufficientFundsException(asset, amount, available());
        }
        held = held.add(amount);
    }

    public void release(BigDecimal amount) {
        held = held.subtract(amount);
        if (held.signum() < 0) throw new IllegalStateException("Released more than was held on " + id);
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getAsset() { return asset; }
    public AccountKind getKind() { return kind; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getHeld() { return held; }
}
