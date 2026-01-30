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

            return cashfreeHttpService.initiateTransfer(request);

        } catch (Exception e) {
            log.error("Payout failed", e);
            throw new RuntimeException("Payout failed: " + e.getMessage());
        }
    }

    public TransferStatusResponseDTO checkPayoutStatus(String cfTransferId) {
        try {

            return cashfreeHttpService
                    .getTransferStatusByTransferId(cfTransferId);

        } catch (Exception e) {
            log.error("Status check failed", e);
            throw new RuntimeException("Status check failed: " + e.getMessage());
        }
    }
}