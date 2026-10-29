package dev.nagarjuna.exchange.risk;

import dev.nagarjuna.exchange.engine.OrderType;
import dev.nagarjuna.exchange.order.PlaceOrderCommand;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Optional;

/**
 * Checks an order before it reaches the order book. A rejected order is saved as REJECTED with the
 * rule that stopped it, so there is a record of every blocked attempt.
 *
 * Checks run cheapest first. The rate check goes last so that an order rejected for size or price
 * doesn't use up the trader's allowance.
 */
@Component
public class PreTradeRisk {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final RiskLimits limits;
    private final OrderRateLimiter rateLimiter;
    private final MeterRegistry meters;

    public PreTradeRisk(RiskLimits limits, Clock clock, MeterRegistry meters) {
        this.limits = limits;
        this.rateLimiter = new OrderRateLimiter(limits.maxOrdersPerWindow(), limits.window(), clock);
        this.meters = meters;
    }

    /**
     * @param lastPrice the symbol's last trade price, or null if it hasn't traded yet. Market
     *                  orders are valued at this price; with no trades yet only the size check applies to them.
     */
    public Optional<RiskRejection> check(PlaceOrderCommand cmd, BigDecimal lastPrice) {
        Optional<RiskRejection> rejection = evaluate(cmd, lastPrice);
        rejection.ifPresent(r -> meters.counter("exchange.risk.rejections", "rule", r.rule().name()).increment());
        return rejection;
    }

    private Optional<RiskRejection> evaluate(PlaceOrderCommand cmd, BigDecimal lastPrice) {
        if (cmd.quantity() > limits.maxQuantity()) {
            return reject(RiskRule.MAX_QUANTITY, "%d shares is over the %d limit", cmd.quantity(), limits.maxQuantity());
        }

        BigDecimal price = cmd.type() == OrderType.LIMIT ? cmd.price() : lastPrice;
        if (price != null) {
            BigDecimal notional = price.multiply(BigDecimal.valueOf(cmd.quantity()));
            if (notional.compareTo(limits.maxNotional()) > 0) {
                return reject(RiskRule.MAX_NOTIONAL, "order value %s is over the %s limit",
                        notional.toPlainString(), limits.maxNotional().toPlainString());
            }
        }

        if (cmd.type() == OrderType.LIMIT && lastPrice != null) {
            BigDecimal movePercent = cmd.price().subtract(lastPrice).abs()
                    .multiply(HUNDRED).divide(lastPrice, 2, RoundingMode.HALF_UP);
            if (movePercent.compareTo(limits.priceBandPercent()) > 0) {
                return reject(RiskRule.PRICE_BAND, "price %s is %s%% away from the last trade at %s (limit %s%%)",
                        cmd.price().toPlainString(), movePercent.toPlainString(), lastPrice.toPlainString(),
                        limits.priceBandPercent().toPlainString());
            }
        }

        if (!rateLimiter.tryAcquire(cmd.traderId())) {
            return reject(RiskRule.ORDER_RATE, "more than %d orders in %s", limits.maxOrdersPerWindow(),
                    limits.window().toString().substring(2).toLowerCase());
        }
        return Optional.empty();
    }

    private static Optional<RiskRejection> reject(RiskRule rule, String format, Object... args) {
        return Optional.of(new RiskRejection(rule, format.formatted(args)));
    }
}
