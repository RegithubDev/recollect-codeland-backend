package com.example.payment_services.constant;

public final class TransactionNarration {

    // Private constructor to prevent instantiation
    private TransactionNarration() {}

    // Payin Transactions
    public static final String PAY_IN_SUCCESS = "Order Payment Done Successfully";

    // Refund
    public static final String REFUND_INITIATED = "Order Payment Refund Initiated";
    public static final String REFUND_SUCCESS = "Amount Refunded Successfully";
    public static final String REFUND_FAILED = "Refund Request Failed";

    // Wallet Transactions
    public static final String TO_WALLET = "Order Amount Credited to Wallet";
    public static final String WALLET_DEDUCTION = "Order Amount Debited From Wallet";

    //Top Up
    public static final String WALLET_TOP_UP = "Wallet Money Added";
    // Withdrawal
    public static final String WITHDRAWAL_INITIATED = "Wallet Money Withdrew";
    public static final String WITHDRAWAL_SUCCESS = "Wallet Amount Withdrawn Successfully";
    public static final String WITHDRAWAL_FAILED = "Wallet Amount Withdrawal Failed";

}