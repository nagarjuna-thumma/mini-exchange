package dev.nagarjuna.exchange.api;

import dev.nagarjuna.exchange.engine.OrderType;
import dev.nagarjuna.exchange.engine.Side;
import dev.nagarjuna.exchange.order.ExchangeService;
import dev.nagarjuna.exchange.order.PlaceOrderCommand;
import dev.nagarjuna.exchange.order.PlaceOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final ExchangeService exchange;

    public OrderController(ExchangeService exchange) {
        this.exchange = exchange;
    }

    public record PlaceOrderRequest(
            @NotNull UUID traderId,
            @NotBlank String symbol,
            @NotNull Side side,
            @NotNull OrderType type,
            @DecimalMax("1000000") BigDecimal price,
            @Min(1) @Max(1_000_000) int quantity) {
    }

    /** 201 when the exchange accepted the order (even if it didn't trade), 422 when it was rejected. */
    @PostMapping
    public ResponseEntity<Views.PlaceOrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        PlaceOutcome outcome = exchange.place(new PlaceOrderCommand(
                request.traderId(), request.symbol(), request.side(), request.type(), request.price(), request.quantity()));
        HttpStatus status = outcome.rejected() ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(Views.PlaceOrderResponse.from(outcome));
    }

    @DeleteMapping("/{id}")
    public Views.OrderView cancel(@PathVariable UUID id, @RequestParam String symbol, @RequestParam UUID traderId) {
        return Views.OrderView.from(exchange.cancel(symbol, id, traderId));
    }
}
