package dev.nagarjuna.exchange;

import dev.nagarjuna.exchange.ledger.AccountRepository;
import dev.nagarjuna.exchange.trader.Trader;
import dev.nagarjuna.exchange.trader.TraderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.locations=classpath:db/migration,classpath:db/testdata")
@Import(TestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ReconciliationTest {

    @Autowired TestRestTemplate http;
    @Autowired TraderService traderService;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reconciliationCatchesABalanceEditedOutsideTheLedger() {
        Trader t = traderService.open("recon-" + UUID.randomUUID(), new BigDecimal("500.00"), 0);
        UUID cash = accounts.findByOwnerIdAndAsset(t.getId(), "CAD").orElseThrow().getId();

        Map<?, ?> clean = http.postForObject("/api/ledger/reconciliations", null, Map.class);
        assertThat(clean.get("healthy")).isEqualTo(true);

        // Someone "fixes" a balance with SQL instead of posting a ledger transaction.
        jdbc.update("UPDATE accounts SET balance = balance + 100 WHERE id = ?", cash);
        try {
            Map<?, ?> failed = http.postForObject("/api/ledger/reconciliations", null, Map.class);
            assertThat(failed.get("healthy")).isEqualTo(false);
            assertThat(failed.get("accountsNotMatchingPostings")).isEqualTo(1);
            assertThat(failed.get("assetsNotNetting")).isEqualTo(1);
            assertThat(failed.get("detail").toString()).contains(cash.toString());

            List<?> history = http.getForObject("/api/ledger/reconciliations", List.class);
            assertThat(history).hasSizeGreaterThanOrEqualTo(2);
            assertThat(((Map<?, ?>) history.get(0)).get("healthy")).isEqualTo(false);

            String metrics = http.getForObject("/actuator/metrics/exchange.ledger.healthy", String.class);
            assertThat(metrics).contains("\"value\":0.0");
        } finally {
            jdbc.update("UPDATE accounts SET balance = balance - 100 WHERE id = ?", cash);
        }
    }
}
