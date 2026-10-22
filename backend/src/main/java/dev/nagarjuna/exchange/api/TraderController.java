package dev.nagarjuna.exchange.api;

import dev.nagarjuna.exchange.ledger.AccountRepository;
import dev.nagarjuna.exchange.order.OrderRepository;
import dev.nagarjuna.exchange.order.OrderStatus;
import dev.nagarjuna.exchange.trader.TraderRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * There is no login in this project on purpose: it's about the exchange and the ledger.
 * Authentication with OAuth2/JWT is covered in a separate repo (expense-claims-api).
 */
@RestController
@RequestMapping("/api/traders")
public class TraderController {

    private final TraderRepository traders;
    private final AccountRepository accounts;
    private final OrderRepository orders;

    public TraderController(TraderRepository traders, AccountRepository accounts, OrderRepository orders) {
        this.traders = traders;
        this.accounts = accounts;
        this.orders = orders;
    }

    @GetMapping
    public List<Views.TraderView> traders() {
        return traders.findAllByOrderByUsername().stream().map(t -> new Views.TraderView(t.getId(), t.getUsername())).toList();
    }

    @GetMapping("/{id}/balances")
    public List<Views.BalanceView> balances(@PathVariable UUID id) {
        return accounts.findByOwnerIdOrderByAsset(id).stream().map(Views.BalanceView::from).toList();
    }

    @GetMapping("/{id}/orders")
    public List<Views.OrderView> orders(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean openOnly) {
        var list = openOnly
                ? orders.findByTraderIdAndStatusInOrderByCreatedAtDesc(id, List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED))
                : orders.findTop50ByTraderIdOrderByCreatedAtDesc(id);
        return list.stream().map(Views.OrderView::from).toList();
    }
}
