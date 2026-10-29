package dev.nagarjuna.exchange.risk;

import dev.nagarjuna.exchange.engine.OrderType;
import dev.nagarjuna.exchange.engine.Side;
import dev.nagarjuna.exchange.order.PlaceOrderCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PreTradeRiskTest {

    static final BigDecimal LAST = new BigDecimal("40.00");

    final UUID trader = UUID.randomUUID();
    final MutableClock clock = new MutableClock(Instant.parse("2026-10-29T14:30:00Z"));
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final PreTradeRisk risk = new PreTradeRisk(
            new RiskLimits(1_000, new BigDecimal("20000"), new BigDecimal("10"), 3, Duration.ofSeconds(10)), clock, meters);

    PlaceOrderCommand limit(String price, int qty) {
        return new PlaceOrderCommand(trader, "NRTH", Side.BUY, OrderType.LIMIT, new BigDecimal(price), qty);
    }

    PlaceOrderCommand market(int qty) {
        return new PlaceOrderCommand(trader, "NRTH", Side.SELL, OrderType.MARKET, null, qty);
    }

    RiskRule ruleFor(PlaceOrderCommand cmd, BigDecimal lastPrice) {
        return risk.check(cmd, lastPrice).map(RiskRejection::rule).orElse(null);
    }

    @Test
    void normalOrderPasses() {
        assertThat(risk.check(limit("41.00", 100), LAST)).isEmpty();
    }

    @Test
    void oversizedOrderIsBlocked() {
        assertThat(ruleFor(limit("1.00", 1_001), LAST)).isEqualTo(RiskRule.MAX_QUANTITY);
    }

    @Test
    void orderValueOverTheLimitIsBlocked() {
        // 600 x 40.00 = 24,000, over 20,000.
        assertThat(ruleFor(limit("40.00", 600), LAST)).isEqualTo(RiskRule.MAX_NOTIONAL);
        // Market orders are valued at the last trade.
        assertThat(ruleFor(market(600), LAST)).isEqualTo(RiskRule.MAX_NOTIONAL);
        assertThat(ruleFor(market(400), LAST)).isNull();
    }

    @Test
    void priceFarFromTheLastTradeIsBlocked() {
        // 4000.00 instead of 40.00: the classic fat finger.
        RiskRejection rejection = risk.check(limit("4.00", 10), LAST).orElseThrow();
        assertThat(rejection.rule()).isEqualTo(RiskRule.PRICE_BAND);
        assertThat(rejection.reason()).isEqualTo(
                "Risk check PRICE_BAND: price 4.00 is 90.00% away from the last trade at 40.00 (limit 10%)");

        assertThat(ruleFor(limit("44.00", 10), LAST)).as("exactly 10%% is allowed").isNull();
        assertThat(ruleFor(limit("44.01", 10), LAST)).isEqualTo(RiskRule.PRICE_BAND);
        assertThat(ruleFor(limit("35.99", 10), LAST)).isEqualTo(RiskRule.PRICE_BAND);
    }

    @Test
    void noPriceBandBeforeTheFirstTrade() {
        assertThat(ruleFor(limit("4.00", 10), null)).isNull();
        assertThat(ruleFor(market(10_000), null)).as("size check still applies").isEqualTo(RiskRule.MAX_QUANTITY);
    }

    @Test
    void tooManyOrdersInTheWindowAreBlockedUntilItSlides() {
        for (int i = 0; i < 3; i++) assertThat(ruleFor(limit("40.00", 1), LAST)).isNull();
        assertThat(ruleFor(limit("40.00", 1), LAST)).isEqualTo(RiskRule.ORDER_RATE);

        // Another trader has their own allowance.
        assertThat(risk.check(new PlaceOrderCommand(UUID.randomUUID(), "NRTH", Side.BUY, OrderType.LIMIT,
                LAST, 1), LAST)).isEmpty();

        clock.advance(Duration.ofSeconds(10));
        assertThat(ruleFor(limit("40.00", 1), LAST)).isNull();
    }

    @Test
    void ordersBlockedForOtherReasonsDontUseUpTheRateAllowance() {
        for (int i = 0; i < 5; i++) assertThat(ruleFor(limit("4.00", 1), LAST)).isEqualTo(RiskRule.PRICE_BAND);
        assertThat(ruleFor(limit("40.00", 1), LAST)).isNull();
    }

    @Test
    void rejectionsAreCountedByRule() {
        risk.check(limit("4.00", 1), LAST);
        risk.check(limit("4.00", 1), LAST);
        risk.check(limit("1.00", 5_000), LAST);

        assertThat(meters.counter("exchange.risk.rejections", "rule", "PRICE_BAND").count()).isEqualTo(2);
        assertThat(meters.counter("exchange.risk.rejections", "rule", "MAX_QUANTITY").count()).isEqualTo(1);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
