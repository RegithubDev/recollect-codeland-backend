package com.example.payment_services.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payout_transactions")
@Data
public class PayoutTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", unique = true, nullable = false)
    private String transferId;

    @Column(name = "cf_transfer_id", unique = true, nullable = false)
    private String cfTransferId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "transfer_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal transferAmount;

    @Column(name = "currency", length = 10)
    private String currency = "INR";

    @Column(name = "transfer_mode", length = 20) // NEFT, IMPS, RTGS, UPI, card
    private String transferMode;

    @Column(name = "purpose", length = 50)
    private String purpose;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "status", length = 30)
    private String status; // created, processing, processed, failed, rejected

    @Column(name = "status_code")
    private String statusCode;

    @Column(name = "status_description")
    private String statusDescription;

    @Column(name = "beneficiary_details", columnDefinition = "JSON")
    private String beneficiaryDetails;

    @Column(name = "fees", precision = 10, scale = 2)
    private BigDecimal fees;

    @Column(name = "tax", precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_uid", updatable = false)
    private String createdUid;

    @Column(name = "updated_uid")
    private String updatedUid;
}