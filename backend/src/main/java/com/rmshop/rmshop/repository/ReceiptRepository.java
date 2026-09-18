package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    Optional<Receipt> findBySaleId(Long saleId);
}