package com.example.payment_services.service;

import com.example.payment_services.dto.coa.UpdateCoaRequest;
import com.example.payment_services.entity.ChartOfAccounts;
import com.example.payment_services.repository.ChartOfAccountsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChartOfAccountsService {

    private final ChartOfAccountsRepository repo;

    public List<ChartOfAccounts> getAll() {
        return repo.findAll();
    }

    public ChartOfAccounts getById(String accountId) {
        return repo.findById(accountId)
                .orElseThrow(() -> new RuntimeException("COA not found"));
    }

    public ChartOfAccounts update(String accountId, UpdateCoaRequest req) {
        ChartOfAccounts coa = getById(accountId);

        if (req.getDescription() != null)
            coa.setDescription(req.getDescription());

        if (req.getIsActive() != null)
            coa.setIsActive(req.getIsActive());

        return repo.save(coa);
    }
}
