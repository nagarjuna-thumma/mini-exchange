package dev.nagarjuna.exchange.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    LedgerTransaction(String type, String description) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
}
