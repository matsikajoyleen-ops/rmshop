package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {
}