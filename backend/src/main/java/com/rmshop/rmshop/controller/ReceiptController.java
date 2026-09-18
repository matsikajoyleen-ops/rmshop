package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.model.Receipt;
import com.rmshop.rmshop.service.ReceiptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

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

    // FR-11: manager-only per the SRS — not yet enforced, same open access-control gap as everywhere else
    @GetMapping
    public List<ReceiptResponse> listAll() {
        return receiptService.listAll().stream().map(ReceiptResponse::from).toList();
    }

    public record SaveReceiptRequest(Long saleId) {}

    public record ReceiptResponse(Long id, Long saleId, String receiptNo, LocalDateTime savedAt, LocalDateTime printedAt) {
        static ReceiptResponse from(Receipt receipt) {
            return new ReceiptResponse(receipt.getId(), receipt.getSale().getId(), receipt.getReceiptNo(), receipt.getSavedAt(), receipt.getPrintedAt());
        }
    }
}