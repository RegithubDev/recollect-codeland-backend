package com.example.payment_services.service;

import com.example.payment_services.dto.wallet.PayoutResponseDTO;
import com.example.payment_services.dto.wallet.WalletWithdrawal;
import com.example.payment_services.entity.GeneralLedger;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.example.payment_services.util.SecurityUtil.getCurrentUserId;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final LedgerService ledgerService;

    @Transactional
    public PayoutResponseDTO payoutToWallet(PayoutResponseDTO request) {
        ledgerService.recordWalletPayout(
                request.getReferenceId(),
                request.getFundAccountId(),
                request.getCustomerId(),
                request.getContactId(),
                request.getAmount(),
                getCurrentUserId()
        );
        return request;
    }

    @Transactional
    public WalletWithdrawal walletWithdrawalApproved(WalletWithdrawal request) {
        ledgerService.recordWithdrawalApproved(
                request.getReferenceId(),
                request.getFundAccountId(),
                request.getCustomerId(),
                request.getContactId(),
                request.getAmount(),
                getCurrentUserId()
        );
        return request;
    }

    @Transactional
    public BigDecimal getWalletBalance(String userID) {
        return ledgerService.getCustomerWalletBalance(userID);
    }

    @Transactional
    public Page<GeneralLedger> getWalletTransactions(
            String userId, Pageable pageable, LocalDate startDate,
            LocalDate endDate, GeneralLedger.EntryType entryType, String transactionType) {

        return ledgerService.getWalletTransactionsByUser(
                userId, pageable, startDate, endDate, entryType, transactionType);
    }

    @Transactional
    public Page<GeneralLedger> getAllLedgerEntries(
            Pageable pageable, String accountId, String customerId,
            String transactionId, GeneralLedger.EntryType entryType,
            LocalDate startDate, LocalDate endDate) {

        return ledgerService.getAllLedgerEntries(
                pageable, accountId, customerId, transactionId, entryType, startDate, endDate);
    }

    @Transactional
    public long getRecentTransactionsCount(String userId, int days) {
        return ledgerService.getRecentTransactionsCount(userId, days);
    }

    @Transactional
    public BigDecimal getTotalCredits(String userId) {
        return ledgerService.getTotalCreditsForUser(userId);
    }

    @Transactional
    public BigDecimal getTotalDebits(String userId) {
        return ledgerService.getTotalDebitsForUser(userId);
    }
}