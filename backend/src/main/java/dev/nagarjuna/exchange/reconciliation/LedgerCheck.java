package dev.nagarjuna.exchange.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LedgerCheck(boolean healthy, List<AssetTotal> totalsByAsset, List<UUID> accountsNotMatchingPostings,
                          List<UUID> accountsWithWrongHolds) {

    public record AssetTotal(String asset, BigDecimal total) {
    }

    public List<String> assetsNotNettingToZero() {
        return totalsByAsset.stream().filter(t -> t.total().signum() != 0).map(AssetTotal::asset).toList();
    }
}
