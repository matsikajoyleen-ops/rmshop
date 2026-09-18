package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
}