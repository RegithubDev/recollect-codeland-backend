package com.example.payment_services.controller;

import com.example.payment_services.dto.wallet.PayoutResponseDTO;
import com.example.payment_services.dto.wallet.WalletWithdrawal;
import com.example.payment_services.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Slf4j
public class WalletController {

    private final WalletService walletService;

    /**
     * POST /api/wallet/from/payout
     * Transfer money to customer wallet
     */
    @PostMapping("/from/payout")
    public ResponseEntity<?> toWallet(@RequestBody PayoutResponseDTO request) {
        try {
            log.info("Wallet payout request: {}", request);
            PayoutResponseDTO payout = walletService.payoutToWallet(request);
            return ResponseEntity.ok(payout);
        } catch (RuntimeException e) {
            log.warn("Wallet payout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error initiating payout to wallet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * POST /api/wallet/approve/withdraw
     * Approve withdrawal from wallet to bank
     */
    @PostMapping("/approve/withdraw")
    public ResponseEntity<?> walletApprovedWithdraw(@RequestBody WalletWithdrawal request) {
        try {
            log.info("Wallet withdrawal approval request: {}", request);
            WalletWithdrawal approve = walletService.walletWithdrawalApproved(request);
            return ResponseEntity.ok(approve);
        } catch (RuntimeException e) {
            log.warn("Wallet withdrawal approval failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error wallet withdrawal approval process", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/wallet/user/{userId}/balance
     * Get user wallet balance - FIXED PATH
     */
    @GetMapping("/user/{userId}/balance")  // FIXED: Added {userId} in path
    public ResponseEntity<?> userWalletBalance(@PathVariable String userId) {
        try {
            log.info("Get wallet balance for user: {}", userId);
            BigDecimal balance = walletService.GetWalletBalance(userId);  // FIXED: Method name
            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "balance", balance,
                    "currency", "INR"
            ));
        } catch (RuntimeException e) {
            log.warn("Get wallet balance failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting wallet balance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }
}