package com.rmshop.rmshop.service;

import com.rmshop.rmshop.model.Receipt;
import com.rmshop.rmshop.model.Sale;
import com.rmshop.rmshop.repository.ReceiptRepository;
import com.rmshop.rmshop.repository.SaleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final SaleRepository saleRepository;

    public ReceiptService(ReceiptRepository receiptRepository, SaleRepository saleRepository) {
        this.receiptRepository = receiptRepository;
        this.saleRepository = saleRepository;
    }

    public Receipt saveReceipt(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("No sale with id " + saleId));
        String receiptNo = "RCPT-" + LocalDate.now().getYear() + "-" + String.format("%06d", sale.getId());
        return receiptRepository.save(new Receipt(sale, receiptNo));
    }

    public Receipt markPrinted(Long receiptId) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("No receipt with id " + receiptId));
        receipt.setPrintedAt(LocalDateTime.now());
        return receiptRepository.save(receipt);
    }

    public Optional<Receipt> findBySaleId(Long saleId) {
        return receiptRepository.findBySaleId(saleId);
    }

    // FR-11: manager browses the full receipt archive
    public List<Receipt> listAll() {
        return receiptRepository.findAll();
    }
}