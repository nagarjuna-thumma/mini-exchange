package dev.nagarjuna.exchange.reconciliation;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the full ledger audit on a schedule (hourly by default) and stores the result. The database
 * constraints should make a failure impossible, so a failed run means a bug or a manual edit and is
 * logged at ERROR for alerting. {@code exchange_ledger_healthy} is 1 after a clean run and 0 after a failed one.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final LedgerAudit audit;
    private final ReconciliationRunRepository runs;
    private final Clock clock;
    private final AtomicInteger healthy = new AtomicInteger(1);

    public ReconciliationJob(LedgerAudit audit, ReconciliationRunRepository runs, Clock clock, MeterRegistry meters) {
        this.audit = audit;
        this.runs = runs;
        this.clock = clock;
        Gauge.builder("exchange.ledger.healthy", healthy, AtomicInteger::get)
                .description("1 if the last reconciliation found no problems").register(meters);
    }

    @Scheduled(cron = "${exchange.reconciliation.cron:0 0 * * * *}")
    public ReconciliationRun run() {
        Instant started = clock.instant();
        LedgerCheck check = audit.check();
        ReconciliationRun run = runs.save(ReconciliationRun.of(started, Duration.between(started, clock.instant()), check));
        healthy.set(check.healthy() ? 1 : 0);
        if (check.healthy()) {
            log.info("Ledger reconciliation {} passed in {} ms", run.getId(), run.getDurationMillis());
        } else {
            log.error("Ledger reconciliation {} FAILED: {}", run.getId(), run.getDetail());
        }
        return run;
    }
}
