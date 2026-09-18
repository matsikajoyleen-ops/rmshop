package com.rmshop.rmshop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "quantity", nullable = false)
    private int quantity = 0;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 10;

    @Column(name = "critical_stock_threshold", nullable = false)
    private int criticalStockThreshold = 3;

    @Column(name = "last_restocked_at")
    private LocalDateTime lastRestockedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum StockLevel {
        SUFFICIENT, LOW, CRITICAL
    }

    public Product() {}

    public Product(String name, int quantity, BigDecimal price) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }

    // FR-7: flags low/critical stock based on this product's own thresholds
    public StockLevel getStockLevel() {
        if (quantity <= criticalStockThreshold) return StockLevel.CRITICAL;
        if (quantity <= lowStockThreshold) return StockLevel.LOW;
        return StockLevel.SUFFICIENT;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public int getCriticalStockThreshold() { return criticalStockThreshold; }
    public void setCriticalStockThreshold(int criticalStockThreshold) { this.criticalStockThreshold = criticalStockThreshold; }

    public LocalDateTime getLastRestockedAt() { return lastRestockedAt; }
    public void setLastRestockedAt(LocalDateTime lastRestockedAt) { this.lastRestockedAt = lastRestockedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}