package com.rmshop.rmshop.service;

import com.rmshop.rmshop.model.Product;
import com.rmshop.rmshop.model.Sale;
import com.rmshop.rmshop.model.SaleItem;
import com.rmshop.rmshop.model.User;
import com.rmshop.rmshop.repository.ProductRepository;
import com.rmshop.rmshop.repository.SaleRepository;
import com.rmshop.rmshop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public SaleService(SaleRepository saleRepository, ProductRepository productRepository, UserRepository userRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // A single requested line within a sale: which product, how many
    public record SaleLine(Long productId, int quantity) {}

    // FR-15: prices each line, decrements stock, and saves the whole sale as one
    // all-or-nothing transaction — same pattern as ProductService.restock()
    @Transactional
    public Sale processSale(Long employeeId, List<SaleLine> lines) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("No employee with id " + employeeId));

        Sale sale = new Sale(employee);

        for (SaleLine line : lines) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new IllegalArgumentException("No product with id " + line.productId()));

            if (product.getQuantity() < line.quantity()) {
                throw new IllegalStateException("Not enough stock for " + product.getName());
            }

            sale.getItems().add(new SaleItem(sale, product, line.quantity(), product.getPrice()));

            product.setQuantity(product.getQuantity() - line.quantity());
            productRepository.save(product);
        }

        BigDecimal total = sale.getItems().stream()
                .map(SaleItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sale.setTotalAmount(total);

        return saleRepository.save(sale);
    }

    public List<Sale> listSales() {
        return saleRepository.findAll();
    }
}