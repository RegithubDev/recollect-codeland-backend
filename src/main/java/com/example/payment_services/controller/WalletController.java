package com.example.payment_services.controller;

import com.example.payment_services.dto.wallet.PayoutResponseDTO;
import com.example.payment_services.dto.wallet.WalletWithdrawal;
import com.example.payment_services.entity.GeneralLedger;
import com.example.payment_services.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.print.Pageable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Wallet Management", description = "APIs for managing user wallets, withdrawals, and balance operations")
public class WalletController {

    private final WalletService walletService;

    @Operation(
            summary = "Payout to Wallet",
            description = "Transfers money from payout system to user's wallet balance"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Payout to wallet completed successfully",
                    content = @Content(schema = @Schema(implementation = PayoutResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid payout request or insufficient funds",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User wallet not found",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Payout validation failed",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    @PostMapping("/from/payout")
    public ResponseEntity<?> toWallet(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payout transfer details to credit wallet balance",
                    required = true
            )
            @RequestBody PayoutResponseDTO request) {
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

    @Operation(
            summary = "Approve Wallet Withdrawal",
            description = "Approves withdrawal request from user's wallet to their bank account"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Withdrawal approved and processed successfully",
                    content = @Content(schema = @Schema(implementation = WalletWithdrawal.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid withdrawal request or insufficient balance",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Withdrawal request or user wallet not found",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Withdrawal already processed or in progress",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during withdrawal processing",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    @PostMapping("/approve/withdraw")
    public ResponseEntity<?> walletApprovedWithdraw(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Withdrawal approval request details",
                    required = true
            )
            @RequestBody WalletWithdrawal request) {
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

    @Operation(
            summary = "Get Wallet Balance",
            description = "Retrieves the current available balance in a user's wallet"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet balance retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid user ID format",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User wallet not found",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    @GetMapping("/user/{userId}/balance")
    public ResponseEntity<?> userWalletBalance(
            @Parameter(
                    description = "Unique user identifier",
                    example = "USER_12345",
                    required = true
            )
            @PathVariable String userId) {
        try {
            log.info("Get wallet balance for user: {}", userId);
            BigDecimal balance = walletService.getWalletBalance(userId);
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

    @Operation(
            summary = "Get Wallet Transaction History",
            description = "Retrieves ledger entries for a specific user's wallet transactions"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet transaction history retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Page.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User wallet not found",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    @GetMapping("/user/{userId}/transactions")
    public ResponseEntity<?> getUserWalletTransactions(
            @Parameter(description = "Unique user identifier", required = true)
            @PathVariable String userId,

            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field (entryDate, amount, etc.)", example = "entryDate")
            @RequestParam(defaultValue = "entryDate") String sortBy,

            @Parameter(description = "Sort direction (ASC/DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String direction,

            @Parameter(description = "Filter by start date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Filter by end date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Filter by entry type (DEBIT/CREDIT)")
            @RequestParam(required = false) GeneralLedger.EntryType entryType,

            @Parameter(description = "Filter by transaction type")
            @RequestParam(required = false) String transactionType) {

        try {
            log.info("Getting wallet transactions for user: {}", userId);

            // Create pageable with sorting
            Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction)
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = (Pageable) PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            // Call service method (you'll need to implement this in WalletService/LedgerService)
            Page<GeneralLedger> transactions = walletService.getWalletTransactions(
                    userId, pageable, startDate, endDate, entryType, transactionType);

            return ResponseEntity.ok(transactions);

        } catch (RuntimeException e) {
            log.warn("Get wallet transactions failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting wallet transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @Operation(
            summary = "Get All Ledger Entries",
            description = "Retrieves all general ledger entries with filtering and pagination"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Ledger entries retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Page.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    @GetMapping("/ledger/entries")
    public ResponseEntity<?> getAllLedgerEntries(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "50")
            @RequestParam(defaultValue = "50") int size,

            @Parameter(description = "Sort field", example = "entryDate")
            @RequestParam(defaultValue = "entryDate") String sortBy,

            @Parameter(description = "Sort direction", example = "DESC")
            @RequestParam(defaultValue = "DESC") String direction,

            @Parameter(description = "Filter by account ID")
            @RequestParam(required = false) String accountId,

            @Parameter(description = "Filter by customer ID")
            @RequestParam(required = false) String customerId,

            @Parameter(description = "Filter by transaction ID")
            @RequestParam(required = false) String transactionId,

            @Parameter(description = "Filter by entry type")
            @RequestParam(required = false) GeneralLedger.EntryType entryType,

            @Parameter(description = "Filter by start date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Filter by end date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            log.info("Getting all ledger entries with filters");

            // Create pageable with sorting
            Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction)
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = (Pageable) PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            // Call service method (you'll need to implement this in LedgerService)
            Page<GeneralLedger> ledgerEntries = walletService.getAllLedgerEntries(
                    pageable, accountId, customerId, transactionId, entryType, startDate, endDate);

            return ResponseEntity.ok(ledgerEntries);

        } catch (Exception e) {
            log.error("Error getting ledger entries", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @Operation(
            summary = "Get Wallet Summary",
            description = "Retrieves summary statistics for a user's wallet"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet summary retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User wallet not found",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<?> getWalletSummary(
            @Parameter(description = "Unique user identifier", required = true)
            @PathVariable String userId) {

        try {
            log.info("Getting wallet summary for user: {}", userId);

            // Get balance
            BigDecimal balance = walletService.getWalletBalance(userId);

            // Get recent transactions count (last 30 days)
            long recentTransactions = walletService.getRecentTransactionsCount(userId, 30);

            // Get total credits and debits (you'll need to implement these)
            BigDecimal totalCredits = walletService.getTotalCredits(userId);
            BigDecimal totalDebits = walletService.getTotalDebits(userId);

            Map<String, Object> summary = Map.of(
                    "userId", userId,
                    "currentBalance", balance,
                    "totalCredits", totalCredits,
                    "totalDebits", totalDebits,
                    "recentTransactionsCount", recentTransactions,
                    "currency", "INR",
                    "lastUpdated", java.time.LocalDateTime.now()
            );

            return ResponseEntity.ok(summary);

        } catch (RuntimeException e) {
            log.warn("Get wallet summary failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting wallet summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }
}