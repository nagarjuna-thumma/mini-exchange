package dev.nagarjuna.exchange.risk;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Pre-trade limits, set under {@code exchange.risk}. The defaults are loose enough for the demo
 * bots and tight enough to stop a fat-fingered order or a runaway script.
 */
@ConfigurationProperties("exchange.risk")
public record RiskLimits(
        @DefaultValue("50000") int maxQuantity,
        @DefaultValue("1000000") BigDecimal maxNotional,
        @DefaultValue("10") BigDecimal priceBandPercent,
        @DefaultValue("30") int maxOrdersPerWindow,
        @DefaultValue("10s") Duration window) {
}
