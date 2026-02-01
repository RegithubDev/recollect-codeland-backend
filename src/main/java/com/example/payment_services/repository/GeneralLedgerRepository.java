package com.example.payment_services.repository;

import com.example.payment_services.entity.GeneralLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface GeneralLedgerRepository extends JpaRepository<GeneralLedger, Long> {

    // Find by transaction ID
    List<GeneralLedger> findByTransactionId(String transactionId);

    // Find by customer ID and date range
    List<GeneralLedger> findByCustomerIdAndEntryDateBetween(
            String customerId, LocalDate startDate, LocalDate endDate);

    // Find by account ID and date range
    List<GeneralLedger> findByAccountIdAndEntryDateBetween(
            String accountId, LocalDate startDate, LocalDate endDate);

    // Calculate wallet balance
    @Query("SELECT COALESCE(SUM(gl.amount), 0) FROM GeneralLedger gl " +
            "WHERE gl.customerId = :customerId " +
            "AND gl.accountName = :accountName " +
            "AND gl.entryType = :entryType ")
    BigDecimal sumWalletCredits(@Param("customerId") String customerId,
                                @Param("accountName") String accountName,
                                @Param("entryType") GeneralLedger.EntryType entryType);

    @Query("SELECT COALESCE(SUM(gl.amount), 0) FROM GeneralLedger gl " +
            "WHERE gl.customerId = :customerId " +
            "AND gl.accountName = :accountName " +
            "AND gl.entryType = :entryType ")
    BigDecimal sumWalletDebits(@Param("customerId") String customerId,
                               @Param("accountName") String accountName,
                               @Param("entryType") GeneralLedger.EntryType entryType);
}