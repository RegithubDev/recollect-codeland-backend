package com.example.payment_services.repository;

import com.example.payment_services.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction> findByOrderId(String orderId);

    Optional<PaymentTransaction> findByCfOrderId(String cfOrderId);

    Optional<PaymentTransaction> findByCfPaymentId(String cfPaymentId);

    Optional<PaymentTransaction> findByCfRefundId(String cfRefundId);

    List<PaymentTransaction> findByCustomerDetails_CustomerId(String customerId);

    List<PaymentTransaction> findByMerchantId(String merchantId);

    List<PaymentTransaction> findByPaymentStatus(PaymentTransaction.PaymentStatus status);

    List<PaymentTransaction> findByOrderStatus(PaymentTransaction.OrderStatus status);

    List<PaymentTransaction> findByRefundStatus(PaymentTransaction.RefundStatus status);

    List<PaymentTransaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT p FROM PaymentTransaction p WHERE p.paymentStatus = 'SUCCESS' AND p.refundStatus IS NULL AND p.orderExpiryTime < :now")
    List<PaymentTransaction> findExpiredSuccessfulPayments(@Param("now") LocalDateTime now);

    @Query("SELECT SUM(p.orderAmount) FROM PaymentTransaction p WHERE p.paymentStatus = 'SUCCESS' AND p.createdAt BETWEEN :start AND :end")
    BigDecimal getTotalRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(p.refundAmount) FROM PaymentTransaction p WHERE p.refundStatus = 'SUCCESS' AND p.refundCreatedAt BETWEEN :start AND :end")
    BigDecimal getTotalRefundsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}