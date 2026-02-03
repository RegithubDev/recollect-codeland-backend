// GeneralLedgerRepository.java
package com.example.payment_services.repository;

import com.example.payment_services.entity.GeneralLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeneralLedgerRepository extends JpaRepository<GeneralLedger, Long> {

    // Wallet balance calculation
    @Query("SELECT SUM(gl.amount) FROM GeneralLedger gl WHERE gl.customerId = :customerId AND gl.accountId = :accountId AND gl.entryType = :entryType")
    Optional<BigDecimal> sumAmountByCustomerAndAccountAndEntryType(
            @Param("customerId") String customerId,
            @Param("accountId") String accountId,
            @Param("entryType") GeneralLedger.EntryType entryType);

    // Company balance calculation
    @Query("SELECT SUM(gl.amount) FROM GeneralLedger gl WHERE gl.accountId = :accountId AND gl.entryType = :entryType")
    Optional<BigDecimal> sumAmountByAccountAndEntryType(
            @Param("accountId") String accountId,
            @Param("entryType") GeneralLedger.EntryType entryType);

    // Get all transactions for customer
    List<GeneralLedger> findByCustomerId(String customerId);

    // Get all transactions for order
    List<GeneralLedger> findByOrderId(String orderId);

    // Get all transactions for account
    List<GeneralLedger> findByAccountId(String accountId);

    @Query("SELECT COUNT(g) FROM GeneralLedger g WHERE g.customerId = :customerId " +
            "AND g.accountId = :accountId AND g.entryDate >= :cutoffDate")
    long countByCustomerIdAndAccountIdAndEntryDateAfter(
            @Param("customerId") String customerId,
            @Param("accountId") String accountId,
            @Param("cutoffDate") LocalDate cutoffDate);

    Page<GeneralLedger> findAll(Specification<GeneralLedger> spec, Pageable pageable);

    List<GeneralLedger> findByTransactionId(String transactionId);
}