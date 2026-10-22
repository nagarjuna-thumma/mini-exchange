package dev.nagarjuna.exchange.trader;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    private String symbol;
    private String name;
    private String currency;

    protected Instrument() {
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
}
