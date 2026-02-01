package com.example.payment_services.seeder;

import com.example.payment_services.entity.ChartOfAccounts;
import com.example.payment_services.repository.ChartOfAccountsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ChartOfAccountsSeeder implements CommandLineRunner {

    private final ChartOfAccountsRepository chartOfAccountsRepository;

    @Override
    public void run(String... args) throws Exception {
        seedChartOfAccounts();
    }

    private void seedChartOfAccounts() {
        // Check if already seeded
        if (chartOfAccountsRepository.count() > 0) {
            System.out.println("Chart of Accounts already seeded.");
            return;
        }
        // EXACTLY THESE 5 ACCOUNTS AS PER YOUR TABLE
        List<ChartOfAccounts> accounts = Arrays.asList(
                // 1001 - Company Bank A/C
                createAccount("1001", "Company Bank A/C",
                        ChartOfAccounts.AccountType.ASSET,
                        ChartOfAccounts.TransactionType.REAL,
                        "Main company bank account for real money transactions"),

                // 2001 - Customer Payout Expenses
                createAccount("2001", "Customer Payout Expenses",
                        ChartOfAccounts.AccountType.EXPENSE,
                        ChartOfAccounts.TransactionType.WALLET,
                        "Expenses incurred for customer payouts from wallet"),

                // 3001 - Customer Wallet Liability
                createAccount("3001", "Customer Wallet Liability",
                        ChartOfAccounts.AccountType.LIABILITY,
                        ChartOfAccounts.TransactionType.WALLET,
                        "Amount owed to customers in their wallets"),

                // 4001 - Payout Pending / Clearing
                createAccount("4001", "Payout Pending / Clearing",
                        ChartOfAccounts.AccountType.CLEARING,
                        ChartOfAccounts.TransactionType.REAL,
                        "Temporary account for pending payouts (real money)"),

                // 5001 - Sales Revenue
                createAccount("5001", "Sales Revenue",
                        ChartOfAccounts.AccountType.INCOME,
                        ChartOfAccounts.TransactionType.REAL,
                        "Revenue from product/service sales (real money)")
        );

        try {
            chartOfAccountsRepository.saveAll(accounts);
            System.out.println("Created seed Chart of Accounts");

        } catch (Exception e) {
            System.err.println("Failed to seed Chart of Accounts: " + e.getMessage());
        }
    }

    private ChartOfAccounts createAccount(String accountId, String accountName,
                                          ChartOfAccounts.AccountType accountType,
                                          ChartOfAccounts.TransactionType transactionType,
                                          String description) {

        LocalDateTime now = LocalDateTime.now();

        ChartOfAccounts account = new ChartOfAccounts();
        account.setAccountId(accountId);
        account.setAccountName(accountName);
        account.setAccountType(accountType);
        account.setTransactionType(transactionType);
        account.setDescription(description);
        account.setIsActive(true);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        return account;
    }
}