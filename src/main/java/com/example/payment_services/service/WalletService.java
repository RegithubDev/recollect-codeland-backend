package com.example.payment_services.service;

import com.example.payment_services.dto.wallet.PayoutResponseDTO;
import com.example.payment_services.dto.wallet.WalletWithdrawal;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static com.example.payment_services.util.SecurityUtil.getCurrentUserId;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final LedgerService ledgerService;

    @Transactional
    public PayoutResponseDTO payoutToWallet(PayoutResponseDTO request) {
        ledgerService.recordWalletPayout(request.getReferenceId(), request.getFundAccountId(),request.getCustomerId(),
                request.getContactId(),request.getAmount(), getCurrentUserId());
        return request;
    }

    @Transactional
    public WalletWithdrawal walletWithdrawalApproved(WalletWithdrawal request) {
        ledgerService.recordWithdrawalApproved(request.getReferenceId(), request.getFundAccountId(),request.getCustomerId(),
                request.getContactId(),request.getAmount(), getCurrentUserId());
        return request;
    }

    @Transactional
    public BigDecimal getWalletBalance(String userID) {
        return ledgerService.getCustomerWalletBalance(userID);
    }
}