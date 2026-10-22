package dev.nagarjuna.exchange.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderBookTest {

    private final OrderBook book = new OrderBook("TEST");
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @Test
    void nonCrossingOrdersRestOnTheBook() {
        book.match(BookOrder.limit(alice, Side.BUY, "10.00", 100));
        book.match(BookOrder.limit(bob, Side.SELL, "10.05", 50));

        assertThat(book.bestBid()).contains(new BigDecimal("10.00"));
        assertThat(book.bestAsk()).contains(new BigDecimal("10.05"));
        assertThat(book.size()).isEqualTo(2);
    }

    @Test
    void tradesHappenAtTheRestingOrdersPrice() {
        book.match(BookOrder.limit(alice, Side.SELL, "10.00", 100));

        MatchResult result = book.match(BookOrder.limit(bob, Side.BUY, "10.50", 100));

        assertThat(result.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.price()).isEqualByComparingTo("10.00");
            assertThat(fill.quantity()).isEqualTo(100);
            assertThat(fill.buyerId()).isEqualTo(bob);
            assertThat(fill.sellerId()).isEqualTo(alice);
        });
        assertThat(book.size()).isZero();
    }

    @Test
    void ordersAtTheSamePriceFillInArrivalOrder() {
        BookOrder first = BookOrder.limit(alice, Side.SELL, "10.00", 50);
        BookOrder second = BookOrder.limit(carol, Side.SELL, "10.00", 50);
        book.match(first);
        book.match(second);

        MatchResult result = book.match(BookOrder.limit(bob, Side.BUY, "10.00", 60));

        assertThat(result.fills()).extracting(Fill::restingOrderId).containsExactly(first.id(), second.id());
        assertThat(result.fills()).extracting(Fill::quantity).containsExactly(50, 10);
        assertThat(book.restingOrders(Side.SELL)).singleElement().satisfies(o -> {
            assertThat(o.id()).isEqualTo(second.id());
            assertThat(o.remaining()).isEqualTo(40);
        });
    }

    @Test
    void betterPricesFillFirstEvenIfTheyArrivedLater() {
        book.match(BookOrder.limit(alice, Side.SELL, "10.10", 10));
        BookOrder cheaper = BookOrder.limit(carol, Side.SELL, "10.00", 10);
        book.match(cheaper);

        MatchResult result = book.match(BookOrder.limit(bob, Side.BUY, "10.10", 10));

        assertThat(result.fills()).singleElement().extracting(Fill::restingOrderId).isEqualTo(cheaper.id());
    }

    @Test
    void partiallyFilledLimitOrderRestsWithTheRemainder() {
        book.match(BookOrder.limit(alice, Side.SELL, "10.00", 30));

        MatchResult result = book.match(BookOrder.limit(bob, Side.BUY, "10.00", 100));

        assertThat(result.filledQuantity()).isEqualTo(30);
        assertThat(result.rested()).isTrue();
        assertThat(book.restingOrders(Side.BUY)).singleElement().extracting(BookOrder::remaining).isEqualTo(70);
    }

    @Test
    void marketOrderSweepsSeveralLevels() {
        book.match(BookOrder.limit(alice, Side.SELL, "10.00", 10));
        book.match(BookOrder.limit(alice, Side.SELL, "10.01", 10));
        book.match(BookOrder.limit(carol, Side.SELL, "10.05", 10));

        MatchResult result = book.match(BookOrder.market(bob, Side.BUY, 25));

        assertThat(result.fills()).extracting(f -> f.price().toPlainString()).containsExactly("10.00", "10.01", "10.05");
        assertThat(result.fills()).extracting(Fill::quantity).containsExactly(10, 10, 5);
        assertThat(book.bestAsk()).contains(new BigDecimal("10.05"));
    }

    @Test
    void marketOrderNeverRestsAndDropsWhatItCannotFill() {
        book.match(BookOrder.limit(alice, Side.BUY, "9.90", 40));

        MatchResult result = book.match(BookOrder.market(bob, Side.SELL, 100));

        assertThat(result.filledQuantity()).isEqualTo(40);
        assertThat(result.rested()).isFalse();
        assertThat(result.unfilledCancelled()).isEqualTo(60);
        assertThat(book.size()).isZero();
    }

    @Test
    void limitOrderStopsAtItsPrice() {
        book.match(BookOrder.limit(alice, Side.SELL, "10.00", 10));
        book.match(BookOrder.limit(alice, Side.SELL, "10.20", 10));

        MatchResult result = book.match(BookOrder.limit(bob, Side.BUY, "10.10", 30));

        assertThat(result.filledQuantity()).isEqualTo(10);
        assertThat(book.bestBid()).contains(new BigDecimal("10.10"));
        assertThat(book.bestAsk()).contains(new BigDecimal("10.20"));
    }

    @Test
    void selfTradeCancelsTheOlderRestingOrderInsteadOfMatching() {
        BookOrder ownAsk = BookOrder.limit(bob, Side.SELL, "10.00", 10);
        book.match(ownAsk);
        book.match(BookOrder.limit(alice, Side.SELL, "10.01", 10));

        MatchResult result = book.match(BookOrder.limit(bob, Side.BUY, "10.01", 10));

        assertThat(result.selfTradeCancels()).singleElement().satisfies(c -> assertThat(c.order().id()).isEqualTo(ownAsk.id()));
        assertThat(result.fills()).singleElement().satisfies(f -> assertThat(f.sellerId()).isEqualTo(alice));
        assertThat(book.contains(ownAsk.id())).isFalse();
    }

    @Test
    void cancelRemovesTheOrderAndEmptyLevels() {
        BookOrder bid = BookOrder.limit(alice, Side.BUY, "10.00", 10);
        book.match(bid);

        assertThat(book.cancel(bid.id())).contains(bid);
        assertThat(book.bestBid()).isEmpty();
        assertThat(book.cancel(bid.id())).isEmpty();
    }

    @Test
    void depthAggregatesEachPriceLevel() {
        book.match(BookOrder.limit(alice, Side.BUY, "10.00", 10));
        book.match(BookOrder.limit(carol, Side.BUY, "10.00", 15));
        book.match(BookOrder.limit(alice, Side.BUY, "9.95", 5));

        Depth depth = book.depth(10);

        assertThat(depth.bids()).extracting(Depth.Level::quantity).containsExactly(25, 5);
        assertThat(depth.bids()).extracting(Depth.Level::orders).containsExactly(2, 1);
    }

    @Test
    void costToBuyWalksTheAsksAndSkipsTheBuyersOwnOrders() {
        book.match(BookOrder.limit(bob, Side.SELL, "9.00", 100));
        book.match(BookOrder.limit(alice, Side.SELL, "10.00", 5));
        book.match(BookOrder.limit(carol, Side.SELL, "11.00", 5));

        assertThat(book.costToBuy(bob, 8)).isEqualByComparingTo("83.00"); // 5 x 10 + 3 x 11
    }

    @Test
    void restoringAnOrderThatWouldCrossIsRefused() {
        book.restore(BookOrder.limit(alice, Side.SELL, "10.00", 10));

        assertThatThrownBy(() -> book.restore(BookOrder.limit(bob, Side.BUY, "10.00", 10)))
                .isInstanceOf(IllegalStateException.class);
    }
}
