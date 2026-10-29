package dev.nagarjuna.exchange.api;

import dev.nagarjuna.exchange.reconciliation.LedgerAudit;
import dev.nagarjuna.exchange.reconciliation.LedgerCheck;
import dev.nagarjuna.exchange.reconciliation.ReconciliationJob;
import dev.nagarjuna.exchange.reconciliation.ReconciliationRun;
import dev.nagarjuna.exchange.reconciliation.ReconciliationRunRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LedgerController {

    private final LedgerAudit audit;
    private final ReconciliationJob reconciliation;
    private final ReconciliationRunRepository runs;

    public LedgerController(LedgerAudit audit, ReconciliationJob reconciliation, ReconciliationRunRepository runs) {
        this.audit = audit;
        this.reconciliation = reconciliation;
        this.runs = runs;
    }

    @GetMapping("/api/ledger/check")
    public LedgerCheck check() {
        return audit.check();
    }

    /** The last 20 scheduled (or manual) reconciliation runs, newest first. */
    @GetMapping("/api/ledger/reconciliations")
    public List<ReconciliationRun> reconciliations() {
        return runs.findTop20ByOrderByIdDesc();
    }

    /** Runs a reconciliation now, outside the schedule. */
    @PostMapping("/api/ledger/reconciliations")
    public ReconciliationRun reconcileNow() {
        return reconciliation.run();
    }
}
