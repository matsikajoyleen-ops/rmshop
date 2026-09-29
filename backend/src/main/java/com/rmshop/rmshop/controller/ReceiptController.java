package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.model.Receipt;
import com.rmshop.rmshop.service.ReceiptService;
import com.rmshop.rmshop.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final UserService userService;

    public ReceiptController(ReceiptService receiptService, UserService userService) {
        this.receiptService = receiptService;
        this.userService = userService;
    }

    // Open: every employee needs these right after a sale
    @PostMapping
    public ReceiptResponse saveReceipt(@RequestBody SaveReceiptRequest request) {
        return ReceiptResponse.from(receiptService.saveReceipt(request.saleId()));
    }

    @PutMapping("/{id}/print")
    public ResponseEntity<ReceiptResponse> markPrinted(@PathVariable Long id) {
        return ResponseEntity.ok(ReceiptResponse.from(receiptService.markPrinted(id)));
    }

    @GetMapping("/by-sale/{saleId}")
    public ResponseEntity<ReceiptResponse> getBySale(@PathVariable Long saleId) {
        return receiptService.findBySaleId(saleId)
                .map(r -> ResponseEntity.ok(ReceiptResponse.from(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Manager-only: the full archive (FR-11)
    @GetMapping
    public List<ReceiptResponse> listAll(@RequestHeader(value = "X-User-Id", required = false) Long requesterId) {
        userService.requireManager(requesterId);
        return receiptService.listAll().stream().map(ReceiptResponse::from).toList();
    }

    public record SaveReceiptRequest(Long saleId) {}

    public record ReceiptResponse(Long id, Long saleId, String receiptNo, LocalDateTime savedAt, LocalDateTime printedAt) {
        static ReceiptResponse from(Receipt receipt) {
            return new ReceiptResponse(receipt.getId(), receipt.getSale().getId(), receipt.getReceiptNo(), receipt.getSavedAt(), receipt.getPrintedAt());
        }
    }
}