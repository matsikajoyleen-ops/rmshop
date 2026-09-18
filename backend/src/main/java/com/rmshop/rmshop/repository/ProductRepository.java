package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}