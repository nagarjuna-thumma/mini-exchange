package dev.nagarjuna.exchange.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOwnerIdOrderByAsset(UUID ownerId);

    Optional<Account> findByOwnerIdAndAsset(UUID ownerId, String asset);

    List<Account> findByOwnerIdInAndAssetIn(Collection<UUID> ownerIds, Collection<String> assets);

    Optional<Account> findFirstByKindAndAsset(AccountKind kind, String asset);

    @Query("select a.asset as asset, sum(a.balance) as total from Account a group by a.asset order by a.asset")
    List<AssetTotal> totalsByAsset();

    interface AssetTotal {
        String getAsset();
        BigDecimal getTotal();
    }
}
