package dev.nagarjuna.exchange.ledger;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String asset, BigDecimal needed, BigDecimal available) {
        super("Not enough %s: needs %s, %s available".formatted(asset, needed.toPlainString(), available.toPlainString()));
    }
}
