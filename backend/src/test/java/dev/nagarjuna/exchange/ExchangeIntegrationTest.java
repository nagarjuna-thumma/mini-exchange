package dev.nagarjuna.exchange;

import dev.nagarjuna.exchange.engine.OrderType;
import dev.nagarjuna.exchange.engine.Side;
import dev.nagarjuna.exchange.ledger.Account;
import dev.nagarjuna.exchange.ledger.AccountRepository;
import dev.nagarjuna.exchange.order.ExchangeService;
import dev.nagarjuna.exchange.order.OrderProcessor;
import dev.nagarjuna.exchange.order.OrderRepository;
import dev.nagarjuna.exchange.order.OrderStatus;
import dev.nagarjuna.exchange.order.PlaceOrderCommand;
import dev.nagarjuna.exchange.order.PlaceOutcome;
import dev.nagarjuna.exchange.trader.Trader;
import dev.nagarjuna.exchange.trader.TraderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.locations=classpath:db/migration,classpath:db/testdata")
@Import(TestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ExchangeIntegrationTest {

    @Autowired ExchangeService exchange;
    @Autowired OrderProcessor processor;
    @Autowired TraderService traderService;
    @Autowired AccountRepository accounts;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;

    Trader seller;
    Trader buyer;

    @BeforeEach
    void openTraders() {
        seller = traderService.open("seller-" + UUID.randomUUID(), new BigDecimal("1000.00"), 500);
        buyer = traderService.open("buyer-" + UUID.randomUUID(), new BigDecimal("10000.00"), 0);
    }

    private PlaceOutcome limit(Trader t, String symbol, Side side, String price, int qty) {
        return exchange.place(new PlaceOrderCommand(t.getId(), symbol, side, OrderType.LIMIT, new BigDecimal(price), qty));
    }

    private Account account(Trader t, String asset) {
        return accounts.findByOwnerIdAndAsset(t.getId(), asset).orElseThrow();
    }

    private void assertLedgerHealthy() {
        @SuppressWarnings("unchecked")
        Map<String, Object> check = http.getForObject("/api/ledger/check", Map.class);
        assertThat(check.get("healthy")).as("ledger check: %s", check).isEqualTo(true);
    }

    @Test
    void matchingOrdersSettleCashAndSharesAndReleaseHolds() {
        limit(seller, "T1", Side.SELL, "20.00", 100);
        PlaceOutcome buy = limit(buyer, "T1", Side.BUY, "20.50", 60);

        assertThat(buy.trades()).singleElement().satisfies(t -> {
            assertThat(t.getPrice()).isEqualByComparingTo("20.00");
            assertThat(t.getQuantity()).isEqualTo(60);
        });
        assertThat(buy.order().getStatus()).isEqualTo(OrderStatus.FILLED);

        // Bought 60 at 20.00 = 1,200. The extra 0.50 a share reserved by the limit is released.
        assertThat(account(buyer, "CAD").getBalance()).isEqualByComparingTo("8800.00");
        assertThat(account(buyer, "CAD").getHeld()).isEqualByComparingTo("0");
        assertThat(account(buyer, "T1").getBalance()).isEqualByComparingTo("60");
        assertThat(account(seller, "CAD").getBalance()).isEqualByComparingTo("2200.00");
        // 40 shares still listed for sale, so still held.
        assertThat(account(seller, "T1").getBalance()).isEqualByComparingTo("440");
        assertThat(account(seller, "T1").getHeld()).isEqualByComparingTo("40");
        assertLedgerHealthy();
    }

    @Test
    void orderThatCannotBePaidForIsRejectedAndLeavesNoTrace() {
        PlaceOutcome outcome = limit(buyer, "T2", Side.BUY, "100.00", 101); // needs 10,100, has 10,000

        assertThat(outcome.rejected()).isTrue();
        assertThat(outcome.order().getRejectReason()).contains("Not enough cash");
        assertThat(exchange.depth("T2").bids()).noneMatch(l -> l.price().compareTo(new BigDecimal("100.00")) == 0);
        assertThat(account(buyer, "CAD").getHeld()).isEqualByComparingTo("0");
    }

    @Test
    void cancellingReleasesTheHold() {
        PlaceOutcome bid = limit(buyer, "T3", Side.BUY, "5.00", 100);
        assertThat(account(buyer, "CAD").getHeld()).isEqualByComparingTo("500.00");

        exchange.cancel("T3", bid.order().getId(), buyer.getId());

        assertThat(account(buyer, "CAD").getHeld()).isEqualByComparingTo("0");
        assertThat(orders.findById(bid.order().getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> exchange.cancel("T3", bid.order().getId(), buyer.getId()))
                .hasMessageContaining("already cancelled");
    }

    @Test
    void onlyTheOwnerCanCancel() {
        PlaceOutcome bid = limit(buyer, "T4", Side.BUY, "5.01", 10);

        assertThatThrownBy(() -> exchange.cancel("T4", bid.order().getId(), seller.getId()))
                .hasMessageContaining("not found");
    }

    @Test
    void ledgerRejectsUnbalancedTransactionsAndEditsToHistory() {
        UUID account = account(buyer, "CAD").getId();
        UUID tx = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.execute("""
                BEGIN;
                INSERT INTO ledger_transactions (id, type, description, created_at) VALUES ('%s', 'TEST', 'free money', now());
                INSERT INTO postings (transaction_id, account_id, amount, balance_after, created_at) VALUES ('%s', '%s', 1000, 0, now());
                COMMIT;
                """.formatted(tx, tx, account)))
                .hasMessageContaining("does not balance");
        jdbc.execute("ROLLBACK");

        assertThatThrownBy(() -> jdbc.update("UPDATE postings SET amount = amount * 2 WHERE account_id = ?", account))
                .hasMessageContaining("append-only");
    }

    @Test
    void bookIsRebuiltExactlyFromTheDatabase() {
        limit(seller, "T5", Side.SELL, "80.00", 30);
        limit(seller, "T5", Side.SELL, "80.00", 20);
        limit(buyer, "T5", Side.BUY, "79.00", 10);
        limit(buyer, "T5", Side.BUY, "80.00", 25); // fills against the first ask

        var live = exchange.depth("T5");
        var rebuilt = processor.rebuild("T5").depth(10);

        assertThat(rebuilt).isEqualTo(live);
    }

    @Test
    void concurrentTradingAcrossSymbolsKeepsTheLedgerConsistent() throws Exception {
        List<Trader> traders = new ArrayList<>();
        for (int i = 0; i < 6; i++) traders.add(traderService.open("crowd-" + UUID.randomUUID(), new BigDecimal("50000.00"), 2_000));
        List<String> symbols = List.of("C1", "C2", "C3");

        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Callable<PlaceOutcome>> tasks = new ArrayList<>();
        Random random = new Random(7);
        for (int i = 0; i < 600; i++) {
            Trader t = traders.get(random.nextInt(traders.size()));
            String symbol = symbols.get(random.nextInt(symbols.size()));
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            int qty = 1 + random.nextInt(50);
            PlaceOrderCommand cmd = random.nextInt(6) == 0
                    ? new PlaceOrderCommand(t.getId(), symbol, side, OrderType.MARKET, null, qty)
                    : new PlaceOrderCommand(t.getId(), symbol, side, OrderType.LIMIT,
                            BigDecimal.valueOf(2_950 + random.nextInt(100), 2), qty);
            tasks.add(() -> exchange.place(cmd));
        }
        List<Future<PlaceOutcome>> results = pool.invokeAll(tasks);
        pool.shutdown();

        long trades = 0;
        for (Future<PlaceOutcome> f : results) trades += f.get().trades().size();
        assertThat(trades).as("the crowd actually traded").isPositive();

        for (Trader t : traders) {
            for (Account a : accounts.findByOwnerIdOrderByAsset(t.getId())) {
                assertThat(a.getBalance()).isGreaterThanOrEqualTo(a.getHeld());
                assertThat(a.getHeld()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            }
        }
        assertLedgerHealthy();
    }
}
