package com.example.payment_services.controller;

import com.example.payment_services.dto.refund.RefundApprovalDTO;
import com.example.payment_services.dto.refund.*;
import com.example.payment_services.service.RefundService;
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

@RestController
@RequestMapping("/api/refund")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Refund Management", description = "APIs for processing and managing refunds")
public class RefundController {

    private final RefundService refundService;

    @Operation(
            summary = "Initiate Refund",
            description = "Creates and initiates a refund request for a specific order"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Refund initiated successfully",
                    content = @Content(schema = @Schema(implementation = RefundResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid refund request or validation failed"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Refund already exists for this order"
            )
    })
    @PostMapping("/initiate/{orderId}")
    public ResponseEntity<RefundResponseDTO> createRefund(
            @Parameter(
                    description = "Order ID for which refund is being initiated",
                    example = "ORD_123456",
                    required = true
            )
            @PathVariable String orderId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Refund request details including amount and reason",
                    required = true
            )
            @RequestBody RefundRequestDTO request) {

        log.info("Creating refund for order: {}", orderId);

        RefundResponseDTO response = refundService.initiateRefund(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get Refund Status",
            description = "Retrieves the current status and details of a specific refund"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Refund details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = RefundResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Refund or order not found"
            )
    })
    @GetMapping("/status/{orderId}/{cfRefundId}")
    public ResponseEntity<RefundResponseDTO> getRefund(
            @Parameter(
                    description = "Order ID associated with the refund",
                    example = "ORD_123456",
                    required = true
            )
            @PathVariable String orderId,

            @Parameter(
                    description = "Cashfree refund reference ID",
                    example = "CF_REFUND_789012",
                    required = true
            )
            @PathVariable String cfRefundId) {

        log.info("Getting refund: orderId={}, refundId={}", orderId, cfRefundId);

        RefundResponseDTO refund = refundService.getRefundDetails(orderId, cfRefundId);
        return ResponseEntity.ok(refund);
    }

//    @Operation(
//        summary = "Get All Refunds for Order",
//        description = "Retrieves all refunds associated with a specific order (placeholder - currently only one refund per order is allowed)"
//    )
//    @ApiResponses(value = {
//        @ApiResponse(
//            responseCode = "200",
//            description = "List of refunds retrieved successfully"
//        ),
//        @ApiResponse(
//            responseCode = "404",
//            description = "Order not found"
//        )
//    })
//    @GetMapping("/{orderId}")
//    public ResponseEntity<RefundResponseDTO[]> getAllRefunds(
//            @Parameter(description = "Order ID", example = "ORD_123456", required = true)
//            @PathVariable String orderId) {
//
//        log.info("Getting all refunds for order: {}", orderId);
//
//        RefundResponseDTO[] refunds = refundService.getAllOrderRefunds(orderId);
//        return ResponseEntity.ok(refunds);
//    }

    @Operation(
            summary = "Check Refund Eligibility",
            description = "Checks if an order is eligible for refund based on business rules"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Eligibility check completed",
                    content = @Content(schema = @Schema(implementation = EligibilityResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            )
    })
    @GetMapping("/eligibility/{orderId}")
    public ResponseEntity<?> checkRefundEligibility(
            @Parameter(
                    description = "Order ID to check eligibility",
                    example = "ORD_123456",
                    required = true
            )
            @PathVariable String orderId) {

        log.info("Checking refund eligibility for order: {}", orderId);

        boolean eligible = refundService.isRefundEligible(orderId);

        return ResponseEntity.ok().body(
                new EligibilityResponse(eligible,
                        eligible ? "Order is eligible for refund" : "Order is not eligible for refund")
        );
    }

    @Operation(
            summary = "Approve Refund",
            description = "Approves a pending refund request (typically used by administrators)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Refund approved successfully",
                    content = @Content(schema = @Schema(implementation = RefundApprovalDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid approval request or refund cannot be approved"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Refund not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Refund already processed"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during refund processing"
            )
    })
    @PostMapping("/approve")
    public ResponseEntity<?> approveRefund(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Refund approval details including approval status and remarks",
                    required = true
            )
            @RequestBody RefundApprovalDTO approvalRequest) {
        try {
            RefundApprovalDTO refund = refundService.handleRefundApproved(approvalRequest);
            return ResponseEntity.ok(refund);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @Schema(description = "Eligibility check response")
    private record EligibilityResponse(
            @Schema(description = "Whether the order is eligible for refund", example = "true")
            boolean eligible,

            @Schema(description = "Detailed message about eligibility", example = "Order is eligible for refund")
            String message
    ) {}
}