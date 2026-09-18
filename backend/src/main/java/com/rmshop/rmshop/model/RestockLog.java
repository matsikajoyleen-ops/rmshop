package com.rmshop.rmshop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "restock_log")
public class RestockLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_added", nullable = false)
    private int quantityAdded;

    @Column(name = "cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(name = "restocked_at", nullable = false)
    private LocalDateTime restockedAt = LocalDateTime.now();

    public RestockLog() {}

    public RestockLog(Product product, int quantityAdded, BigDecimal cost) {
        this.product = product;
        this.quantityAdded = quantityAdded;
        this.cost = cost;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public int getQuantityAdded() { return quantityAdded; }
    public void setQuantityAdded(int quantityAdded) { this.quantityAdded = quantityAdded; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public LocalDateTime getRestockedAt() { return restockedAt; }
    public void setRestockedAt(LocalDateTime restockedAt) { this.restockedAt = restockedAt; }
}