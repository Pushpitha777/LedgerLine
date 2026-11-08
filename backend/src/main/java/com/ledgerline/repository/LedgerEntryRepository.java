package com.ledgerline.repository;

import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    Page<LedgerEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Aggregates total sum of all debits or credits across the entire ledger
     * for mathematical verification of the Double-Entry Balance Invariant.
     */
    @Query("SELECT COALESCE(SUM(l.amountPaise), 0) FROM LedgerEntry l WHERE l.entryType = :entryType")
    long sumAmountByEntryType(@Param("entryType") LedgerEntryType entryType);
}
