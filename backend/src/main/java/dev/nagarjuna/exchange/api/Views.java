package dev.nagarjuna.exchange.api;

import dev.nagarjuna.exchange.ledger.Account;
import dev.nagarjuna.exchange.market.TradeView;
import dev.nagarjuna.exchange.order.Order;
import dev.nagarjuna.exchange.order.PlaceOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class Views {

    private Views() {
    }

    record OrderView(UUID id, String symbol, String side, String type, BigDecimal limitPrice, int quantity,
                     int filledQuantity, BigDecimal averagePrice, String status, String rejectReason,
                     Instant createdAt) {
        static OrderView from(Order o) {
            return new OrderView(o.getId(), o.getSymbol(), o.getSide().name(), o.getType().name(), o.getLimitPrice(),
                    o.getQuantity(), o.getFilledQuantity(), o.getAveragePriceSafe(), o.getStatus().name(),
                    o.getRejectReason(), o.getCreatedAt());
        }
    }

    record PlaceOrderResponse(OrderView order, List<TradeView> trades) {
        static PlaceOrderResponse from(PlaceOutcome outcome) {
            return new PlaceOrderResponse(OrderView.from(outcome.order()), outcome.trades().stream().map(TradeView::from).toList());
        }
    }

    record BalanceView(String asset, BigDecimal balance, BigDecimal held, BigDecimal available) {
        static BalanceView from(Account a) {
            return new BalanceView(a.getAsset(), a.getBalance(), a.getHeld(), a.available());
        }
    }

    record InstrumentView(String symbol, String name, String currency, BigDecimal lastPrice) {
    }

    record TraderView(UUID id, String username) {
    }

    record AssetTotal(String asset, BigDecimal total) {
    }

    record LedgerCheck(boolean healthy, List<AssetTotal> totalsByAsset, List<UUID> accountsNotMatchingPostings,
                       List<UUID> accountsWithWrongHolds) {
    }
}
