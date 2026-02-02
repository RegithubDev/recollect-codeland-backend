package com.example.payment_services.repository;

import com.example.payment_services.entity.PayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, Long> {

    Optional<PayoutTransaction> findByTransferId(String transferId);

    Optional<PayoutTransaction> findByCfTransferId(String cfTransferId);

    Optional<PayoutTransaction> findByReferenceId(String referenceId);

    // Find by customer
    List<PayoutTransaction> findByCustomerId(String customerId);

    // Find by status
    List<PayoutTransaction> findByStatus(String status);

    // Find by date range
    List<PayoutTransaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Find by customer and status
    List<PayoutTransaction> findByCustomerIdAndStatus(String customerId, String status);

    // Find pending payouts older than specific time
    @Query("SELECT p FROM PayoutTransaction p WHERE p.status IN ('pending', 'processing') AND p.createdAt < :cutoffTime")
    List<PayoutTransaction> findStalePayouts(@Param("cutoffTime") LocalDateTime cutoffTime);

    // Find successful payouts within amount range
    @Query("SELECT p FROM PayoutTransaction p WHERE p.status = 'success' AND p.transferAmount BETWEEN :minAmount AND :maxAmount")
    List<PayoutTransaction> findSuccessfulPayoutsByAmountRange(
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount);
}