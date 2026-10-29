package dev.nagarjuna.exchange.api;

import dev.nagarjuna.exchange.risk.RiskLimits;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RiskController {

    private final RiskLimits limits;

    public RiskController(RiskLimits limits) {
        this.limits = limits;
    }

    /** The limits every order is checked against, so a client can validate before sending. */
    @GetMapping("/api/risk/limits")
    public RiskLimits limits() {
        return limits;
    }
}
