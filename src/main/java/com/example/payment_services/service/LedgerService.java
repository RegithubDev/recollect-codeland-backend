package com.example.payment_services.service;

import com.example.payment_services.entity.ChartOfAccounts;
import com.example.payment_services.entity.GeneralLedger;
import com.example.payment_services.repository.ChartOfAccountsRepository;
import com.example.payment_services.repository.GeneralLedgerRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final GeneralLedgerRepository generalLedgerRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;

    // ============ HELPER METHOD ============

    /**
     * Create a ledger entry by copying account details
     */
    private GeneralLedger createLedgerEntry(
            ChartOfAccounts account,
            String paymentTransactionId,
            String transactionId,
            String customerId,
            String orderId,
            BigDecimal amount,
            GeneralLedger.EntryType entryType,
            String userId) {

        GeneralLedger entry = new GeneralLedger();
        entry.setLedgerEntryId(generateLedgerEntryId(entryType));
        entry.setPaymentTransactionId(paymentTransactionId);
        entry.setTransactionId(transactionId);
        entry.setEntryDate(LocalDate.now());
        entry.setCustomerId(customerId);
        entry.setOrderId(orderId);
        entry.setAmount(amount);

        // Copy account details from ChartOfAccounts
        entry.setAccountId(account.getAccountId());
        entry.setAccountName(account.getAccountName());
        entry.setAccountType(account.getAccountType().name());
        entry.setLedgerType(account.getTransactionType().name());
        entry.setDescription(account.getDescription());

        entry.setEntryType(entryType);
        entry.setCreatedUid(userId);
        entry.setUpdatedUid(userId);

        return entry;
    }

    private String generateLedgerEntryId(GeneralLedger.EntryType entryType) {
        String prefix = entryType == GeneralLedger.EntryType.DEBIT ? "DEB" : "CRED";
        return prefix + "-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    // ============ SCENARIO METHODS ============

    /**
     * SCENARIO 1: Payment Success (Checkout completed)
     * 1001 (Asset) DEBIT = Bank balance increases
     * 1002 (Clearing) CREDIT = Clear pending amount
     */
    @Transactional
    public void recordPaymentSuccess(
            String paymentTransactionId, String transactionId,
            String customerId, String orderId, BigDecimal amount, String userId) {

        ChartOfAccounts companyBank = chartOfAccountsRepository.findById("1001")
                .orElseThrow(() -> new RuntimeException("Account 1001 not found"));

        ChartOfAccounts clearingAccount = chartOfAccountsRepository.findById("1002")
                .orElseThrow(() -> new RuntimeException("Account 1002 not found"));

        // Debit: Bank balance increases
        GeneralLedger debitEntry = createLedgerEntry(
                companyBank, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.DEBIT, userId);

        // Credit: Clear pending amount
        GeneralLedger creditEntry = createLedgerEntry(
                clearingAccount, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.CREDIT, userId);

        generalLedgerRepository.save(debitEntry);
        generalLedgerRepository.save(creditEntry);

        log.info("Recorded Payment Success: Transaction={}, Amount={}", transactionId, amount);
    }

    /**
     * SCENARIO 2: Refund Approved (before processing)
     * 1003 (Income) DEBIT = Reduce revenue
     * 1004 (Clearing) CREDIT = Move to pending
     */
    @Transactional
    public void recordRefundApproved(
            String paymentTransactionId, String transactionId,
            String customerId, String orderId, BigDecimal amount, String userId) {

        ChartOfAccounts salesRevenue = chartOfAccountsRepository.findById("5001")
                .orElseThrow(() -> new RuntimeException("Account 5001 not found"));

        ChartOfAccounts clearingAccount = chartOfAccountsRepository.findById("4001")
                .orElseThrow(() -> new RuntimeException("Account 4001 not found"));

        // Debit: Reduce revenue
        GeneralLedger debitEntry = createLedgerEntry(
                salesRevenue, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.DEBIT, userId);

        // Credit: Move to pending
        GeneralLedger creditEntry = createLedgerEntry(
                clearingAccount, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.CREDIT, userId);

        generalLedgerRepository.save(debitEntry);
        generalLedgerRepository.save(creditEntry);

        log.info("Recorded Refund Approved: Transaction={}, Amount={}", transactionId, amount);
    }

    /**
     * SCENARIO 3: Refund Processed Success
     * 1005 (Clearing) DEBIT = Clear pending
     * 1006 (Asset) CREDIT = Bank balance decreases
     */
    @Transactional
    public void recordRefundProcessedSuccess(
            String paymentTransactionId, String transactionId,
            String customerId, String orderId, BigDecimal amount, String userId) {

        ChartOfAccounts clearingAccount = chartOfAccountsRepository.findById("1005")
                .orElseThrow(() -> new RuntimeException("Account 1005 not found"));

        ChartOfAccounts companyBank = chartOfAccountsRepository.findById("1006")
                .orElseThrow(() -> new RuntimeException("Account 1006 not found"));

        // Debit: Clear pending
        GeneralLedger debitEntry = createLedgerEntry(
                clearingAccount, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.DEBIT, userId);

        // Credit: Bank balance decreases
        GeneralLedger creditEntry = createLedgerEntry(
                companyBank, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.CREDIT, userId);

        generalLedgerRepository.save(debitEntry);
        generalLedgerRepository.save(creditEntry);

        log.info("Recorded Refund Processed Success: Transaction={}, Amount={}", transactionId, amount);
    }

    /**
     * SCENARIO 4: Refund Failed
     * 1007 (Clearing) DEBIT = Clear pending
     * 1008 (Income) CREDIT = Increase in revenue
     */
    @Transactional
    public void recordRefundFailed(
            String paymentTransactionId, String transactionId,
            String customerId, String orderId, BigDecimal amount, String userId) {

        ChartOfAccounts clearingAccount = chartOfAccountsRepository.findById("1007")
                .orElseThrow(() -> new RuntimeException("Account 1007 not found"));

        ChartOfAccounts salesRevenue = chartOfAccountsRepository.findById("1008")
                .orElseThrow(() -> new RuntimeException("Account 1008 not found"));

        // Debit: Clear pending
        GeneralLedger debitEntry = createLedgerEntry(
                clearingAccount, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.DEBIT, userId);

        // Credit: Increase in revenue
        GeneralLedger creditEntry = createLedgerEntry(
                salesRevenue, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.CREDIT, userId);

        generalLedgerRepository.save(debitEntry);
        generalLedgerRepository.save(creditEntry);

        log.info("Recorded Refund Failed: Transaction={}, Amount={}", transactionId, amount);
    }

    /**
     * SCENARIO 5: Wallet Payout (To customer wallet)
     * 1009 (Expense) DEBIT = Increase expense
     * 1010 (Liability) CREDIT = Create wallet liability
     * for payout we have taking ref id, fund account id, customer id, contact id, amount, user id
     */
    @Transactional
    public void recordWalletPayout(
            String paymentTransactionId, String transactionId,
            String customerId, String orderId, BigDecimal amount, String userId) {

        ChartOfAccounts payoutExpense = chartOfAccountsRepository.findById("2001")
                .orElseThrow(() -> new RuntimeException("Account 2001  not found"));

        ChartOfAccounts walletLiability = chartOfAccountsRepository.findById("3001")
                .orElseThrow(() -> new RuntimeException("Account 3001 not found"));

        // Debit: Increase expense
        GeneralLedger debitEntry = createLedgerEntry(
                payoutExpense, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.DEBIT, userId);

        // Credit: Create wallet liability
        GeneralLedger creditEntry = createLedgerEntry(
                walletLiability, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.CREDIT, userId);

        generalLedgerRepository.save(debitEntry);
        generalLedgerRepository.save(creditEntry);

        log.info("Recorded Wallet Payout: Transaction={}, Amount={}", transactionId, amount);
    }

    /**
     * SCENARIO 6: Withdrawal Approved
     * 1011 (Liability) DEBIT = Reduce wallet liability
     * 1012 (Clearing) CREDIT = Move to pending
     */
    @Transactional
    public void recordWithdrawalApproved(
            String paymentTransactionId, String transactionId,
            String customerId, String orderId, BigDecimal amount, String userId) {

        ChartOfAccounts walletLiability = chartOfAccountsRepository.findById("3001")
                .orElseThrow(() -> new RuntimeException("Account 3001 not found"));

        ChartOfAccounts clearingAccount = chartOfAccountsRepository.findById("4001")
                .orElseThrow(() -> new RuntimeException("Account 4001 not found"));

        // Debit: Reduce wallet liability
        GeneralLedger debitEntry = createLedgerEntry(
                walletLiability, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.DEBIT, userId);

        // Credit: Move to pending
        GeneralLedger creditEntry = createLedgerEntry(
                clearingAccount, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.CREDIT, userId);

        generalLedgerRepository.save(debitEntry);
        generalLedgerRepository.save(creditEntry);

        log.info("Recorded Withdrawal Approved: Transaction={}, Amount={}", transactionId, amount);
    }

    /**
     * SCENARIO 7: Withdrawal Processed Success
     * 1013 (Clearing) DEBIT = Clear pending
     * 1014 (Asset) CREDIT = Bank balance decreases
     */
    @Transactional
    public void recordWithdrawalProcessedSuccess(
            String paymentTransactionId, String transactionId,
            String customerId, String orderId, BigDecimal amount, String userId) {

        ChartOfAccounts clearingAccount = chartOfAccountsRepository.findById("1013")
                .orElseThrow(() -> new RuntimeException("Account 1013 not found"));

        ChartOfAccounts companyBank = chartOfAccountsRepository.findById("1014")
                .orElseThrow(() -> new RuntimeException("Account 1014 not found"));

        // Debit: Clear pending
        GeneralLedger debitEntry = createLedgerEntry(
                clearingAccount, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.DEBIT, userId);

        // Credit: Bank balance decreases
        GeneralLedger creditEntry = createLedgerEntry(
                companyBank, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.CREDIT, userId);

        generalLedgerRepository.save(debitEntry);
        generalLedgerRepository.save(creditEntry);

        log.info("Recorded Withdrawal Processed Success: Transaction={}, Amount={}", transactionId, amount);
    }

    /**
     * SCENARIO 8: Withdrawal Failed
     * 1015 (Clearing) DEBIT = Clear pending
     * 1016 (Liability) CREDIT = Return to wallet liability
     */
    @Transactional
    public void recordWithdrawalFailed(
            String paymentTransactionId, String transactionId,
            String customerId, String orderId, BigDecimal amount, String userId) {

        ChartOfAccounts clearingAccount = chartOfAccountsRepository.findById("1015")
                .orElseThrow(() -> new RuntimeException("Account 1015 not found"));

        ChartOfAccounts walletLiability = chartOfAccountsRepository.findById("1016")
                .orElseThrow(() -> new RuntimeException("Account 1016 not found"));

        // Debit: Clear pending
        GeneralLedger debitEntry = createLedgerEntry(
                clearingAccount, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.DEBIT, userId);

        // Credit: Return to wallet liability
        GeneralLedger creditEntry = createLedgerEntry(
                walletLiability, paymentTransactionId, transactionId,
                customerId, orderId, amount, GeneralLedger.EntryType.CREDIT, userId);

        generalLedgerRepository.save(debitEntry);
        generalLedgerRepository.save(creditEntry);

        log.info("Recorded Withdrawal Failed: Transaction={}, Amount={}", transactionId, amount);
    }

    // ============ WALLET BALANCE CALCULATION ============

    /**
     * Get customer wallet balance
     * Formula: Sum(CREDIT to 3001) - Sum(DEBIT from 3001)
     */
    @Transactional(readOnly = true)
    public BigDecimal getCustomerWalletBalance(String customerId) {
        BigDecimal totalCredits = generalLedgerRepository
                .sumAmountByCustomerAndAccountAndEntryType(customerId, "3001", GeneralLedger.EntryType.CREDIT)
                .orElse(BigDecimal.ZERO);

        BigDecimal totalDebits = generalLedgerRepository
                .sumAmountByCustomerAndAccountAndEntryType(customerId, "3001", GeneralLedger.EntryType.DEBIT)
                .orElse(BigDecimal.ZERO);

        return totalCredits.subtract(totalDebits);
    }

    /**
     * Get company bank balance
     */
    @Transactional(readOnly = true)
    public BigDecimal getCompanyBankBalance() {
        BigDecimal totalDebits = generalLedgerRepository
                .sumAmountByAccountAndEntryType("1001", GeneralLedger.EntryType.DEBIT)
                .orElse(BigDecimal.ZERO);

        BigDecimal totalCredits = generalLedgerRepository
                .sumAmountByAccountAndEntryType("1001", GeneralLedger.EntryType.CREDIT)
                .orElse(BigDecimal.ZERO);

        return totalDebits.subtract(totalCredits); // Assets: Debits - Credits
    }

    /**
     * Get total sales revenue
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalSalesRevenue() {
        BigDecimal totalCredits = generalLedgerRepository
                .sumAmountByAccountAndEntryType("5001", GeneralLedger.EntryType.CREDIT)
                .orElse(BigDecimal.ZERO);

        BigDecimal totalDebits = generalLedgerRepository
                .sumAmountByAccountAndEntryType("5001", GeneralLedger.EntryType.DEBIT)
                .orElse(BigDecimal.ZERO);

        return totalCredits.subtract(totalDebits); // Income: Credits - Debits
    }

    // ============ NEW METHODS FOR WALLET TRANSACTIONS ============

    /**
     * Get wallet transactions for a specific user
     */
    @Transactional(readOnly = true)
    public Page<GeneralLedger> getWalletTransactionsByUser(
            String userId, Pageable pageable, LocalDate startDate,
            LocalDate endDate, GeneralLedger.EntryType entryType, String transactionType) {

        // Create specification for filtering
        Specification<GeneralLedger> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by wallet liability account (3001) and customerId
            predicates.add(cb.equal(root.get("accountId"), "3001"));
            predicates.add(cb.equal(root.get("customerId"), userId));

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("entryDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("entryDate"), endDate));
            }
            if (entryType != null) {
                predicates.add(cb.equal(root.get("entryType"), entryType));
            }
            if (transactionType != null && !transactionType.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("transactionId")),
                        "%" + transactionType.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return generalLedgerRepository.findAll(spec, pageable);
    }

    /**
     * Get all ledger entries with filters
     */
    @Transactional(readOnly = true)
    public Page<GeneralLedger> getAllLedgerEntries(
            Pageable pageable, String accountId, String customerId,
            String transactionId, GeneralLedger.EntryType entryType,
            LocalDate startDate, LocalDate endDate) {

        // Create specification for filtering
        Specification<GeneralLedger> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (accountId != null && !accountId.isEmpty()) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (customerId != null && !customerId.isEmpty()) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (transactionId != null && !transactionId.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("transactionId")),
                        "%" + transactionId.toLowerCase() + "%"));
            }
            if (entryType != null) {
                predicates.add(cb.equal(root.get("entryType"), entryType));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("entryDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("entryDate"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return generalLedgerRepository.findAll(spec, pageable);
    }

    /**
     * Get recent transactions count for a user
     */
    @Transactional(readOnly = true)
    public long getRecentTransactionsCount(String userId, int days) {
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        return generalLedgerRepository.countByCustomerIdAndAccountIdAndEntryDateAfter(
                userId, "3001", cutoffDate);
    }

    /**
     * Get total credits for a user
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalCreditsForUser(String userId) {
        return generalLedgerRepository
                .sumAmountByCustomerAndAccountAndEntryType(
                        userId, "3001", GeneralLedger.EntryType.CREDIT)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Get total debits for a user
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalDebitsForUser(String userId) {
        return generalLedgerRepository
                .sumAmountByCustomerAndAccountAndEntryType(
                        userId, "3001", GeneralLedger.EntryType.DEBIT)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Get ledger entry by ID
     */
    @Transactional(readOnly = true)
    public GeneralLedger getLedgerEntryById(String ledgerEntryId) {
        return generalLedgerRepository.findById(Long.valueOf(ledgerEntryId))
                .orElseThrow(() -> new RuntimeException("Ledger entry not found: " + ledgerEntryId));
    }

    /**
     * Get ledger entries by transaction ID
     */
    @Transactional(readOnly = true)
    public List<GeneralLedger> getLedgerEntriesByTransactionId(String transactionId) {
        return generalLedgerRepository.findByTransactionId(transactionId);
    }
}