package dev.nagarjuna.exchange.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    List<Posting> findTop50ByAccountIdOrderByIdDesc(UUID accountId);

    /** Accounts whose stored balance doesn't equal the sum of their postings. Should always be empty. */
    @Query(value = """
            SELECT a.id FROM accounts a
            LEFT JOIN postings p ON p.account_id = a.id
            GROUP BY a.id, a.balance
            HAVING a.balance <> COALESCE(SUM(p.amount), 0)
            """, nativeQuery = true)
    List<UUID> accountsOutOfBalance();

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM postings", nativeQuery = true)
    BigDecimal sumOfAllPostings();
}
