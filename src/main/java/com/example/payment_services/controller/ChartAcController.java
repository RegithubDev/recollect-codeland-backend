package com.example.payment_services.controller;

import com.example.payment_services.dto.coa.UpdateCoaRequest;
import com.example.payment_services.entity.ChartOfAccounts;
import com.example.payment_services.service.ChartOfAccountsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coa")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chart of Accounts", description = "API for managing Chart of Accounts (COA)")
public class ChartAcController {

    private final ChartOfAccountsService service;

    @Operation(
            summary = "Get all Chart of Accounts",
            description = "Retrieves complete list of all accounts from the Chart of Accounts"
    )
    @GetMapping
    public ResponseEntity<List<ChartOfAccounts>> getAllChartOfAccounts() {
        log.info("Fetching all Chart of Accounts entries");
        List<ChartOfAccounts> accounts = service.getAll();
        return ResponseEntity.ok(accounts);
    }

    @Operation(
            summary = "Get Chart of Account by ID",
            description = "Retrieves a specific account details from Chart of Accounts by its unique identifier"
    )
    @GetMapping("/{accountId}")
    public ResponseEntity<ChartOfAccounts> getChartOfAccountById(
            @Parameter(description = "Unique identifier of the account", example = "ACC001", required = true)
            @PathVariable String accountId) {
        log.info("Fetching Chart of Account by ID: {}", accountId);
        ChartOfAccounts account = service.getById(accountId);
        return ResponseEntity.ok(account);
    }

    @Operation(
            summary = "Update Chart of Account",
            description = "Updates account details in Chart of Accounts"
    )
    @PutMapping("/{accountId}")
    public ResponseEntity<ChartOfAccounts> updateChartOfAccount(
            @Parameter(description = "Unique identifier of the account to update", example = "ACC001", required = true)
            @PathVariable String accountId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Update request containing new account values",
                    required = true
            )
            @RequestBody UpdateCoaRequest request) {
        log.info("Updating Chart of Account ID: {}", accountId);
        ChartOfAccounts updatedAccount = service.update(accountId, request);
        return ResponseEntity.ok(updatedAccount);
    }
}