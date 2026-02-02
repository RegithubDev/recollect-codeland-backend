package com.example.payment_services.controller;

import com.example.payment_services.dto.wallet.PayoutResponseDTO;
import com.example.payment_services.dto.wallet.WalletWithdrawal;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
}