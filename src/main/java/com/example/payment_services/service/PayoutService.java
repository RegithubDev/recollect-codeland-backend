// PayoutService.java
package com.example.payment_services.service;

import com.example.payment_services.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final CashfreeHttpService cashfreeHttpService;

    public CashfreeTransferResponse processPayout(CashfreeTransferRequest request) {
        try {
            CashfreeTransferResponse transferResponse = cashfreeHttpService.initiateTransfer(request);

            return transferResponse;

        } catch (Exception e) {
            log.error("Payout failed", e);
            throw new RuntimeException("Payout failed: " + e.getMessage());
        }
    }

    public TransferStatusResponseDTO checkPayoutStatus(String cfTransferId) {
        try {
            // For referenceId, you might need a different method
            // Currently we only have getTransferStatusByTransferId
            // You'll need to implement getTransferStatusByReferenceId

            // Temporary: Use transferId as referenceId for testing
            TransferStatusResponseDTO statusResponse = cashfreeHttpService
                    .getTransferStatusByTransferId(cfTransferId);

            return statusResponse;

        } catch (Exception e) {
            log.error("Status check failed", e);
            throw new RuntimeException("Status check failed: " + e.getMessage());
        }
    }

    private boolean isSuccessStatus(String status) {
        return "SUCCESS".equalsIgnoreCase(status) ||
                "SUCCESSFUL".equalsIgnoreCase(status);
    }
}