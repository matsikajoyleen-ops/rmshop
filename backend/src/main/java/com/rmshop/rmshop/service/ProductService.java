package com.rmshop.rmshop.service;

import com.rmshop.rmshop.model.Product;
import com.rmshop.rmshop.model.RestockLog;
import com.rmshop.rmshop.repository.ProductRepository;
import com.rmshop.rmshop.repository.RestockLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final RestockLogRepository restockLogRepository;

    public ProductService(ProductRepository productRepository, RestockLogRepository restockLogRepository) {
        this.productRepository = productRepository;
        this.restockLogRepository = restockLogRepository;
    }

    public Product addProduct(String name, int quantity, BigDecimal price) {
        return productRepository.save(new Product(name, quantity, price));
    }

    public List<Product> listProducts() {
        return productRepository.findAll();
    }

    public List<Product> listLowStockProducts() {
        return productRepository.findAll().stream()
                .filter(p -> p.getStockLevel() != Product.StockLevel.SUFFICIENT)
                .toList();
    }

    @Transactional
    public Product restock(Long productId, int quantityAdded, BigDecimal cost) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("No product with id " + productId));

        restockLogRepository.save(new RestockLog(product, quantityAdded, cost));

        product.setQuantity(product.getQuantity() + quantityAdded);
        product.setLastRestockedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    public Product updateThresholds(Long productId, int lowStockThreshold, int criticalStockThreshold) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("No product with id " + productId));
        product.setLowStockThreshold(lowStockThreshold);
        product.setCriticalStockThreshold(criticalStockThreshold);
        return productRepository.save(product);
    }
}