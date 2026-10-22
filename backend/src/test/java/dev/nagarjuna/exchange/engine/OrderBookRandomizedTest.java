package dev.nagarjuna.exchange.engine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Throws thousands of random orders and cancels at the book and checks the rules that must
 * always hold, whatever happens:
 * the book is never crossed, quantity is never created or lost, and fills respect limit prices.
 */
class OrderBookRandomizedTest {

    @ParameterizedTest
    @ValueSource(longs = {1, 7, 42, 2026, 99_999})
    void invariantsHoldUnderRandomFlow(long seed) {
        Random random = new Random(seed);
        OrderBook book = new OrderBook("RAND");
        List<UUID> traders = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<UUID> placed = new ArrayList<>();

        long submitted = 0, filledTwice = 0, cancelled = 0;

        for (int i = 0; i < 5_000; i++) {
            if (!placed.isEmpty() && random.nextInt(10) == 0) {
                UUID id = placed.get(random.nextInt(placed.size()));
                cancelled += book.cancel(id).map(BookOrder::remaining).orElse(0);
                continue;
            }
            UUID trader = traders.get(random.nextInt(traders.size()));
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            int quantity = 1 + random.nextInt(200);
            BookOrder order = random.nextInt(8) == 0
                    ? BookOrder.market(trader, side, quantity)
                    : BookOrder.limit(trader, side, BigDecimal.valueOf(9_500 + random.nextInt(1_000), 2).toPlainString(), quantity);
            submitted += quantity;
            placed.add(order.id());

            MatchResult result = book.match(order);

            for (Fill fill : result.fills()) {
                assertThat(fill.buyerId()).isNotEqualTo(fill.sellerId());
                if (order.type() == OrderType.LIMIT) {
                    int cmp = fill.price().compareTo(order.price());
                    assertThat(side == Side.BUY ? cmp <= 0 : cmp >= 0).as("fill respects the limit price").isTrue();
                }
            }
            filledTwice += 2L * result.filledQuantity();
            cancelled += result.unfilledCancelled();
            cancelled += result.selfTradeCancels().stream().mapToInt(MatchResult.CancelledOrder::cancelledQuantity).sum();

            if (book.bestBid().isPresent() && book.bestAsk().isPresent()) {
                assertThat(book.bestBid().get()).as("book is never crossed").isLessThan(book.bestAsk().get());
            }
            long resting = book.restingOrders(Side.BUY).stream().mapToLong(BookOrder::remaining).sum()
                    + book.restingOrders(Side.SELL).stream().mapToLong(BookOrder::remaining).sum();
            assertThat(filledTwice + cancelled + resting).as("quantity is conserved").isEqualTo(submitted);
        }
    }
}
