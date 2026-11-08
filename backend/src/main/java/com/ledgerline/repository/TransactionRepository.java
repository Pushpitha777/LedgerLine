package com.ledgerline.repository;

import com.ledgerline.entity.Transaction;
import com.ledgerline.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.user.id = :userId OR t.destinationAccount.user.id = :userId ORDER BY t.createdAt DESC")
    Page<Transaction> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    long countByStatus(TransactionStatus status);
}
