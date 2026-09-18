package com.rmshop.rmshop.repository;

import com.rmshop.rmshop.model.Expenditure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenditureRepository extends JpaRepository<Expenditure, Long> {
    List<Expenditure> findByExpenseDateBetween(LocalDate start, LocalDate end);
}