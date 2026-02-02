package com.example.payment_services.controller;

import com.example.payment_services.dto.coa.UpdateCoaRequest;
import com.example.payment_services.entity.ChartOfAccounts;
import com.example.payment_services.service.ChartOfAccountsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coa/")
@RequiredArgsConstructor
@Slf4j
public class ChartAcController {

    private final ChartOfAccountsService service;

    @GetMapping("/getall")
    public List<ChartOfAccounts> getAll() {
        return service.getAll();
    }

    @GetMapping("/get/{accountId}")
    public ChartOfAccounts getById(@PathVariable String accountId) {
        return service.getById(accountId);
    }

    @PutMapping("/update/{accountId}")
    public ChartOfAccounts update(
            @PathVariable String accountId,
            @RequestBody UpdateCoaRequest request
    ) {
        return service.update(accountId, request);
    }
}