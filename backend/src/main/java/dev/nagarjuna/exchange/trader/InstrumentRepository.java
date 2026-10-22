package dev.nagarjuna.exchange.trader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {
    List<Instrument> findAllByOrderBySymbol();
}
