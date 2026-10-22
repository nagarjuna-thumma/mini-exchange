package dev.nagarjuna.exchange.api;

import dev.nagarjuna.exchange.ledger.AccountRepository;
import dev.nagarjuna.exchange.ledger.PostingRepository;
import dev.nagarjuna.exchange.order.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class LedgerController {

    private final AccountRepository accounts;
    private final PostingRepository postings;
    private final OrderRepository orders;

    public LedgerController(AccountRepository accounts, PostingRepository postings, OrderRepository orders) {
        this.accounts = accounts;
        this.postings = postings;
        this.orders = orders;
    }

    /**
     * Audits the whole ledger in one consistent snapshot:
     * every asset nets to zero across all accounts, every balance equals the sum of its postings,
     * and every hold matches what the trader's open orders reserve.
     */
    @GetMapping("/api/ledger/check")
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Views.LedgerCheck check() {
        List<Views.AssetTotal> totals = accounts.totalsByAsset().stream()
                .map(t -> new Views.AssetTotal(t.getAsset(), t.getTotal())).toList();
        List<UUID> outOfBalance = postings.accountsOutOfBalance();
        List<UUID> wrongHolds = orders.accountsWithWrongHolds();
        boolean healthy = totals.stream().allMatch(t -> t.total().signum() == 0) && outOfBalance.isEmpty() && wrongHolds.isEmpty();
        return new Views.LedgerCheck(healthy, totals, outOfBalance, wrongHolds);
    }
}
