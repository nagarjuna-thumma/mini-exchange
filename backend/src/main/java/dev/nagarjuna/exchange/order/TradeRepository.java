package dev.nagarjuna.exchange.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findTop100BySymbolOrderByIdDesc(String symbol);

    Optional<Trade> findFirstBySymbolOrderByIdDesc(String symbol);
}
