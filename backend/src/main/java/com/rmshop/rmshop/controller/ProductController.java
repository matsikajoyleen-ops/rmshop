package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.model.Product;
import com.rmshop.rmshop.service.ProductService;
import com.rmshop.rmshop.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final UserService userService;

    public ProductController(ProductService productService, UserService userService) {
        this.productService = productService;
        this.userService = userService;
    }

    @PostMapping
    public Product addProduct(@RequestHeader(value = "X-User-Id", required = false) Long requesterId, @RequestBody CreateProductRequest request) {
        userService.requireManager(requesterId);
        return productService.addProduct(request.name(), request.quantity(), request.price());
    }

    // Open: the Sales screen needs this for every employee, every sale
    @GetMapping
    public List<Product> listProducts() {
        return productService.listProducts();
    }

    @GetMapping("/low-stock")
    public List<Product> listLowStockProducts(@RequestHeader(value = "X-User-Id", required = false) Long requesterId) {
        userService.requireManager(requesterId);
        return productService.listLowStockProducts();
    }

    @PostMapping("/{id}/restock")
    public ResponseEntity<Product> restock(@RequestHeader(value = "X-User-Id", required = false) Long requesterId, @PathVariable Long id, @RequestBody RestockRequest request) {
        userService.requireManager(requesterId);
        return ResponseEntity.ok(productService.restock(id, request.quantityAdded(), request.cost()));
    }

    @PutMapping("/{id}/thresholds")
    public ResponseEntity<Product> updateThresholds(@RequestHeader(value = "X-User-Id", required = false) Long requesterId, @PathVariable Long id, @RequestBody UpdateThresholdsRequest request) {
        userService.requireManager(requesterId);
        return ResponseEntity.ok(productService.updateThresholds(id, request.lowStockThreshold(), request.criticalStockThreshold()));
    }

    public record CreateProductRequest(String name, int quantity, BigDecimal price) {}
    public record RestockRequest(int quantityAdded, BigDecimal cost) {}
    public record UpdateThresholdsRequest(int lowStockThreshold, int criticalStockThreshold) {}
}