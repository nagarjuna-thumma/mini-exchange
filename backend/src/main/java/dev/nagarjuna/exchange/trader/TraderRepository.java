package dev.nagarjuna.exchange.trader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TraderRepository extends JpaRepository<Trader, UUID> {
    List<Trader> findAllByOrderByUsername();
    boolean existsByUsername(String username);
}
