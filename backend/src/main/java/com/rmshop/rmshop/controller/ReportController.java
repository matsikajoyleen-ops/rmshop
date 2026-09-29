package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.service.ReportService;
import com.rmshop.rmshop.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final UserService userService;

    public ReportController(ReportService reportService, UserService userService) {
        this.reportService = reportService;
        this.userService = userService;
    }

    @GetMapping("/revenue")
    public ReportService.RevenueSummary getRevenueSummary(
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        userService.requireManager(requesterId);
        return reportService.getRevenueSummary(start, end);
    }

    @GetMapping("/top-employee")
    public Optional<ReportService.EmployeePerformance> getTopEmployee(
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        userService.requireManager(requesterId);
        return reportService.getTopEmployee(start, end);
    }

    @GetMapping("/projected-revenue")
    public ReportService.RevenueProjection getProjectedRevenue(
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "30") int projectedDays) {
        userService.requireManager(requesterId);
        return reportService.getProjectedRevenue(start, end, projectedDays);
    }

    @GetMapping("/reconcile")
    public ReportService.CashReconciliation reconcile(
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam BigDecimal actualCash) {
        userService.requireManager(requesterId);
        return reportService.reconcile(date, actualCash);
    }

    @GetMapping("/most-wanted")
    public List<ReportService.ProductDemand> getMostWantedProducts(
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        userService.requireManager(requesterId);
        return reportService.getMostWantedProducts(start, end);
    }
}