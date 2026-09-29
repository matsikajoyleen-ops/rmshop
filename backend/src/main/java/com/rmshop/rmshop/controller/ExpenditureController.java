package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.model.Expenditure;
import com.rmshop.rmshop.service.ExpenditureService;
import com.rmshop.rmshop.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/expenditures")
public class ExpenditureController {

    private final ExpenditureService expenditureService;
    private final UserService userService;

    public ExpenditureController(ExpenditureService expenditureService, UserService userService) {
        this.expenditureService = expenditureService;
        this.userService = userService;
    }

    @PostMapping
    public Expenditure addExpenditure(@RequestHeader(value = "X-User-Id", required = false) Long requesterId, @RequestBody CreateExpenditureRequest request) {
        userService.requireManager(requesterId);
        return expenditureService.addExpenditure(request.description(), request.amount());
    }

    @GetMapping
    public List<Expenditure> listExpenditures(@RequestHeader(value = "X-User-Id", required = false) Long requesterId) {
        userService.requireManager(requesterId);
        return expenditureService.listExpenditures();
    }

    public record CreateExpenditureRequest(String description, BigDecimal amount) {}
}