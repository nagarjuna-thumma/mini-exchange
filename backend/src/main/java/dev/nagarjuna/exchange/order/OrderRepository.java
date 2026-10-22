package dev.nagarjuna.exchange.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findBySymbolAndStatusInOrderBySeq(String symbol, Collection<OrderStatus> statuses);

    List<Order> findTop50ByTraderIdOrderByCreatedAtDesc(UUID traderId);

    List<Order> findByTraderIdAndStatusInOrderByCreatedAtDesc(UUID traderId, Collection<OrderStatus> statuses);

    @Query(value = "SELECT nextval('order_seq')", nativeQuery = true)
    long nextSeq();

    /**
     * Trader accounts whose held amount doesn't match what their open orders should be reserving.
     * Should always be empty.
     */
    @Query(value = """
            SELECT a.id FROM accounts a
            LEFT JOIN (
                SELECT trader_id, 'CAD' AS asset, SUM(limit_price * (quantity - filled_quantity)) AS expected
                FROM orders WHERE status IN ('NEW', 'PARTIALLY_FILLED') AND side = 'BUY'
                GROUP BY trader_id
                UNION ALL
                SELECT trader_id, symbol AS asset, SUM(quantity - filled_quantity) AS expected
                FROM orders WHERE status IN ('NEW', 'PARTIALLY_FILLED') AND side = 'SELL'
                GROUP BY trader_id, symbol
            ) e ON e.trader_id = a.owner_id AND e.asset = a.asset
            WHERE a.kind = 'TRADER' AND a.held <> COALESCE(e.expected, 0)
            """, nativeQuery = true)
    List<UUID> accountsWithWrongHolds();
}
