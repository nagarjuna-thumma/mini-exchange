package dev.nagarjuna.exchange;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.locations=classpath:db/migration,classpath:db/testdata")
@Import(TestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ApiTest {

    @Autowired TestRestTemplate http;
    @Autowired TraderService traderService;

    @Test
    void placeOrderOverHttp() {
        Trader t = traderService.open("api-" + UUID.randomUUID(), new BigDecimal("1000"), 10);

        ResponseEntity<Map> accepted = http.postForEntity("/api/orders", Map.of(
                "traderId", t.getId(), "symbol", "T6", "side", "SELL", "type", "LIMIT", "price", "99.99", "quantity", 5), Map.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> rejected = http.postForEntity("/api/orders", Map.of(
                "traderId", t.getId(), "symbol", "T6", "side", "SELL", "type", "LIMIT", "price", "99.99", "quantity", 50), Map.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<String> badTick = http.postForEntity("/api/orders", Map.of(
                "traderId", t.getId(), "symbol", "T6", "side", "BUY", "type", "LIMIT", "price", "1.001", "quantity", 1), String.class);
        assertThat(badTick.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> unknown = http.getForEntity("/api/market/NOPE/depth", String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
