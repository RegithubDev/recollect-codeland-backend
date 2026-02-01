package com.example.payment_services.repository;

import com.example.payment_services.entity.ChartOfAccounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, String> {

    @Query("SELECT ca FROM ChartOfAccounts ca " +
            "WHERE ca.accountName = :accountName " +
            "AND ca.transactionType = :transactionType " +
            "AND ca.isActive = true")
    ChartOfAccounts findByAccountNameAndLedgerType(
            @Param("accountName") String accountName,
            @Param("transactionType") ChartOfAccounts.TransactionType transactionType
    );

    Optional<ChartOfAccounts> findByAccountNameAndAccountType(
            String accountName,
            ChartOfAccounts.AccountType accountType
    );
}