package dev.nagarjuna.exchange.reconciliation;

import dev.nagarjuna.exchange.ledger.AccountRepository;
import dev.nagarjuna.exchange.ledger.PostingRepository;
import dev.nagarjuna.exchange.order.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class LedgerAudit {

    private final AccountRepository accounts;
    private final PostingRepository postings;
    private final OrderRepository orders;

    public LedgerAudit(AccountRepository accounts, PostingRepository postings, OrderRepository orders) {
        this.accounts = accounts;
        this.postings = postings;
        this.orders = orders;
    }

    /**
     * Audits the whole ledger in one consistent snapshot:
     * every asset nets to zero across all accounts, every balance equals the sum of its postings,
     * and every hold matches what the trader's open orders reserve.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LedgerCheck check() {
        List<LedgerCheck.AssetTotal> totals = accounts.totalsByAsset().stream()
                .map(t -> new LedgerCheck.AssetTotal(t.getAsset(), t.getTotal())).toList();
        List<UUID> outOfBalance = postings.accountsOutOfBalance();
        List<UUID> wrongHolds = orders.accountsWithWrongHolds();
        boolean healthy = totals.stream().allMatch(t -> t.total().signum() == 0) && outOfBalance.isEmpty() && wrongHolds.isEmpty();
        return new LedgerCheck(healthy, totals, outOfBalance, wrongHolds);
    }
}
