package dev.nagarjuna.exchange.order;

import java.util.List;

/** The order as it ended up, plus any trades it caused. */
public record PlaceOutcome(Order order, List<Trade> trades) {

    public boolean rejected() {
        return order.getStatus() == OrderStatus.REJECTED;
    }
}
