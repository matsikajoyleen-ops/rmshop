package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.model.Sale;
import com.rmshop.rmshop.model.SaleItem;
import com.rmshop.rmshop.service.SaleService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    // FR-15: employee processes a sale
    @PostMapping
    public SaleResponse processSale(@RequestBody ProcessSaleRequest request) {
        Sale sale = saleService.processSale(request.employeeId(), request.lines());
        return SaleResponse.from(sale);
    }

    @GetMapping
    public List<SaleResponse> listSales() {
        return saleService.listSales().stream().map(SaleResponse::from).toList();
    }

    public record ProcessSaleRequest(Long employeeId, List<SaleService.SaleLine> lines) {}

    public record SaleItemResponse(Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        static SaleItemResponse from(SaleItem item) {
            return new SaleItemResponse(
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getSubtotal()
            );
        }
    }

    public record SaleResponse(Long id, Long employeeId, String employeeName, LocalDateTime saleDate, BigDecimal totalAmount, List<SaleItemResponse> items) {
        static SaleResponse from(Sale sale) {
            return new SaleResponse(
                    sale.getId(),
                    sale.getEmployee().getId(),
                    sale.getEmployee().getFullName(),
                    sale.getSaleDate(),
                    sale.getTotalAmount(),
                    sale.getItems().stream().map(SaleItemResponse::from).toList()
            );
        }
    }
}