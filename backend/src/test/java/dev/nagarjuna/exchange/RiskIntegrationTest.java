package dev.nagarjuna.exchange;

import dev.nagarjuna.exchange.engine.OrderType;
import dev.nagarjuna.exchange.engine.Side;
import dev.nagarjuna.exchange.ledger.AccountRepository;
import dev.nagarjuna.exchange.order.ExchangeService;
import dev.nagarjuna.exchange.order.OrderStatus;
import dev.nagarjuna.exchange.order.PlaceOrderCommand;
import dev.nagarjuna.exchange.order.PlaceOutcome;
import dev.nagarjuna.exchange.trader.Trader;
import dev.nagarjuna.exchange.trader.TraderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata",
        "exchange.risk.max-orders-per-window=5",
        "exchange.risk.window=1m"})
@Import(TestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class RiskIntegrationTest {

    @Autowired ExchangeService exchange;
    @Autowired TraderService traderService;
    @Autowired AccountRepository accounts;
    @Autowired TestRestTemplate http;

    private PlaceOutcome limit(Trader t, Side side, String price, int qty) {
        return exchange.place(new PlaceOrderCommand(t.getId(), "R1", side, OrderType.LIMIT, new BigDecimal(price), qty));
    }

    @Test
    void blockedOrdersNeverReachTheBookAndAreRecorded() {
        Trader seller = traderService.open("r-seller-" + UUID.randomUUID(), new BigDecimal("0"), 100);
        Trader buyer = traderService.open("r-buyer-" + UUID.randomUUID(), new BigDecimal("1000000"), 0);
        limit(seller, Side.SELL, "50.00", 10);
        assertThat(limit(buyer, Side.BUY, "50.00", 10).trades()).hasSize(1); // last trade is now 50.00

        PlaceOutcome fatFinger = limit(buyer, Side.BUY, "500.00", 10);

        assertThat(fatFinger.order().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(fatFinger.order().getRejectReason()).startsWith("Risk check PRICE_BAND");
        assertThat(exchange.depth("R1").bids()).isEmpty();
        assertThat(accounts.findByOwnerIdAndAsset(buyer.getId(), "CAD").orElseThrow().getHeld()).isEqualByComparingTo("0");
    }

    @Test
    void scriptSendingTooFastIsThrottledOverHttp() {
        Trader bot = traderService.open("r-bot-" + UUID.randomUUID(), new BigDecimal("100000"), 0);
        Map<String, Object> order = Map.of("traderId", bot.getId(), "symbol", "R2", "side", "BUY",
                "type", "LIMIT", "price", "10.00", "quantity", 1);

        for (int i = 0; i < 5; i++) {
            assertThat(http.postForEntity("/api/orders", order, Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        ResponseEntity<Map> sixth = http.postForEntity("/api/orders", order, Map.class);

        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(((Map<?, ?>) sixth.getBody().get("order")).get("rejectReason").toString()).startsWith("Risk check ORDER_RATE");
    }

    @Test
    void limitsArePublished() {
        Map<?, ?> limits = http.getForObject("/api/risk/limits", Map.class);
        assertThat(limits.get("maxOrdersPerWindow")).isEqualTo(5);
        assertThat(limits.get("priceBandPercent")).isEqualTo(10);
    }
}
