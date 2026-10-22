package dev.nagarjuna.exchange.ledger;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The only way balances change. Every movement is a transaction with postings that net to
 * zero per asset (the database checks this too, at commit).
 */
@Component
public class Ledger {

    private final AccountRepository accounts;
    private final AccountLocks locks;
    private final PostingRepository postings;
    private final EntityManager entityManager;

    public Ledger(AccountRepository accounts, AccountLocks locks, PostingRepository postings, EntityManager entityManager) {
        this.accounts = accounts;
        this.locks = locks;
        this.postings = postings;
        this.entityManager = entityManager;
    }

    public record Leg(Account account, BigDecimal amount) {
    }

    /** Records one balanced transaction. Accounts must already be locked by the caller. */
    public UUID post(String type, String description, List<Leg> legs) {
        LedgerTransaction tx = new LedgerTransaction(type, description);
        entityManager.persist(tx);
        List<Posting> rows = new ArrayList<>();
        for (Leg leg : legs) {
            leg.account().apply(leg.amount());
            rows.add(new Posting(tx.getId(), leg.account().getId(), leg.amount(), leg.account().getBalance()));
        }
        postings.saveAll(rows);
        return tx.getId();
    }

    /** Puts cash or shares into a trader's account from the outside world. */
    public UUID deposit(UUID traderId, String asset, BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Deposit must be positive");
        AccountKind sourceKind = "CAD".equals(asset) ? AccountKind.EXTERNAL : AccountKind.ISSUER;
        Account source = accounts.findFirstByKindAndAsset(sourceKind, asset)
                .orElseThrow(() -> new IllegalStateException("No " + sourceKind + " account for " + asset));
        Account target = accounts.findByOwnerIdAndAsset(traderId, asset)
                .orElseThrow(() -> new IllegalArgumentException("Trader has no " + asset + " account"));

        List<Account> locked = locks.lockInOrder(List.of(source.getId(), target.getId()));
        Account lockedSource = AccountLocks.find(locked, source.getId());
        Account lockedTarget = AccountLocks.find(locked, target.getId());
        return post("DEPOSIT", "Deposit of " + amount.toPlainString() + " " + asset,
                List.of(new Leg(lockedSource, amount.negate()), new Leg(lockedTarget, amount)));
    }
}
