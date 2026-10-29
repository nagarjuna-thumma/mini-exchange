package dev.nagarjuna.exchange.risk;

public record RiskRejection(RiskRule rule, String detail) {

    /** Stored as the order's reject reason. */
    public String reason() {
        return "Risk check " + rule + ": " + detail;
    }
}
