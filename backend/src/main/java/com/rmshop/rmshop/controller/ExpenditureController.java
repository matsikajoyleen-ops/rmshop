package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.model.Expenditure;
import com.rmshop.rmshop.service.ExpenditureService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/expenditures")
public class ExpenditureController {

    private final ExpenditureService expenditureService;

    public ExpenditureController(ExpenditureService expenditureService) {
        this.expenditureService = expenditureService;
    }

    @PostMapping
    public Expenditure addExpenditure(@RequestBody CreateExpenditureRequest request) {
        return expenditureService.addExpenditure(request.description(), request.amount());
    }

    @GetMapping
    public List<Expenditure> listExpenditures() {
        return expenditureService.listExpenditures();
    }

    public record CreateExpenditureRequest(String description, BigDecimal amount) {}
}