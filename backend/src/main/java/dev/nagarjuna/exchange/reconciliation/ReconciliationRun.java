package dev.nagarjuna.exchange.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/** One run of the scheduled ledger audit. Kept so a failure has a time and a record, not just a log line. */
@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant startedAt;

    private long durationMillis;

    private boolean healthy;

    private int assetsNotNetting;

    private int accountsNotMatchingPostings;

    private int accountsWithWrongHolds;

    @Column(length = 2000)
    private String detail;

    protected ReconciliationRun() {
    }

    static ReconciliationRun of(Instant startedAt, Duration took, LedgerCheck check) {
        ReconciliationRun run = new ReconciliationRun();
        run.startedAt = startedAt;
        run.durationMillis = took.toMillis();
        run.healthy = check.healthy();
        run.assetsNotNetting = check.assetsNotNettingToZero().size();
        run.accountsNotMatchingPostings = check.accountsNotMatchingPostings().size();
        run.accountsWithWrongHolds = check.accountsWithWrongHolds().size();
        if (!check.healthy()) {
            String detail = "assets=%s postings=%s holds=%s".formatted(check.assetsNotNettingToZero(),
                    check.accountsNotMatchingPostings(), check.accountsWithWrongHolds());
            run.detail = detail.length() > 2000 ? detail.substring(0, 1997) + "..." : detail;
        }
        return run;
    }

    public Long getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public long getDurationMillis() { return durationMillis; }
    public boolean isHealthy() { return healthy; }
    public int getAssetsNotNetting() { return assetsNotNetting; }
    public int getAccountsNotMatchingPostings() { return accountsNotMatchingPostings; }
    public int getAccountsWithWrongHolds() { return accountsWithWrongHolds; }
    public String getDetail() { return detail; }
}
