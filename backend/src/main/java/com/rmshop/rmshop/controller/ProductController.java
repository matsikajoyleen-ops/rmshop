package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.model.Product;
import com.rmshop.rmshop.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public Product addProduct(@RequestBody CreateProductRequest request) {
        return productService.addProduct(request.name(), request.quantity(), request.price());
    }

    @GetMapping
    public List<Product> listProducts() {
        return productService.listProducts();
    }

    // FR-7: manager's low/critical stock alert list
    @GetMapping("/low-stock")
    public List<Product> listLowStockProducts() {
        return productService.listLowStockProducts();
    }

    // FR-8: record a restock
    @PostMapping("/{id}/restock")
    public ResponseEntity<Product> restock(@PathVariable Long id, @RequestBody RestockRequest request) {
        return ResponseEntity.ok(productService.restock(id, request.quantityAdded(), request.cost()));
    }

    @PutMapping("/{id}/thresholds")
    public ResponseEntity<Product> updateThresholds(@PathVariable Long id, @RequestBody UpdateThresholdsRequest request) {
        return ResponseEntity.ok(productService.updateThresholds(id, request.lowStockThreshold(), request.criticalStockThreshold()));
    }

    public record CreateProductRequest(String name, int quantity, BigDecimal price) {}
    public record RestockRequest(int quantityAdded, BigDecimal cost) {}
    public record UpdateThresholdsRequest(int lowStockThreshold, int criticalStockThreshold) {}
}