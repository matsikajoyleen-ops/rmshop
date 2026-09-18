package com.rmshop.rmshop.service;

import com.rmshop.rmshop.model.Expenditure;
import com.rmshop.rmshop.repository.ExpenditureRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ExpenditureService {

    private final ExpenditureRepository expenditureRepository;

    public ExpenditureService(ExpenditureRepository expenditureRepository) {
        this.expenditureRepository = expenditureRepository;
    }

    public Expenditure addExpenditure(String description, BigDecimal amount) {
        return expenditureRepository.save(new Expenditure(description, amount));
    }

    public List<Expenditure> listExpenditures() {
        return expenditureRepository.findAll();
    }

    public List<Expenditure> listBetween(LocalDate start, LocalDate end) {
        return expenditureRepository.findByExpenseDateBetween(start, end);
    }
}