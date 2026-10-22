package dev.nagarjuna.exchange.trader;

import dev.nagarjuna.exchange.ledger.Account;
import dev.nagarjuna.exchange.ledger.AccountRepository;
import dev.nagarjuna.exchange.ledger.Ledger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TraderService {

    private final TraderRepository traders;
    private final InstrumentRepository instruments;
    private final AccountRepository accounts;
    private final Ledger ledger;

    public TraderService(TraderRepository traders, InstrumentRepository instruments, AccountRepository accounts, Ledger ledger) {
        this.traders = traders;
        this.instruments = instruments;
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /** Opens a trader with a CAD account and one account per listed symbol, then funds them. */
    @Transactional
    public Trader open(String username, BigDecimal cash, int sharesEach) {
        Trader trader = traders.save(new Trader(username));
        accounts.save(Account.forTrader(trader.getId(), "CAD"));
        instruments.findAll().forEach(i -> accounts.save(Account.forTrader(trader.getId(), i.getSymbol())));
        accounts.flush();

        if (cash.signum() > 0) ledger.deposit(trader.getId(), "CAD", cash);
        if (sharesEach > 0) {
            instruments.findAll().forEach(i -> ledger.deposit(trader.getId(), i.getSymbol(), BigDecimal.valueOf(sharesEach)));
        }
        return trader;
    }
}
