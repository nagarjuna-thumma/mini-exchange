package dev.nagarjuna.exchange.ledger;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Takes row locks on accounts, always in account id order. Two transactions touching the same
 * accounts then queue up behind each other instead of deadlocking, whichever symbol they come from.
 *
 * Uses refresh rather than a plain locking query: if an account was already loaded earlier in
 * the transaction, a query would hand back the stale copy. Refresh re-reads it under the lock.
 */
@Component
public class AccountLocks {

    private final EntityManager entityManager;

    public AccountLocks(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Account> lockInOrder(Collection<UUID> ids) {
        return ids.stream()
                .distinct()
                .sorted()
                .map(this::lock)
                .toList();
    }

    private Account lock(UUID id) {
        Account account = entityManager.find(Account.class, id);
        if (account == null) throw new IllegalStateException("No account " + id);
        entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        return account;
    }

    public static Account find(List<Account> locked, UUID id) {
        return locked.stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Account " + id + " was not locked"));
    }
}
