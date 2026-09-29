package com.rmshop.rmshop.service;

import com.rmshop.rmshop.model.Expenditure;
import com.rmshop.rmshop.model.RestockLog;
import com.rmshop.rmshop.model.Sale;
import com.rmshop.rmshop.model.SaleItem;
import com.rmshop.rmshop.repository.ExpenditureRepository;
import com.rmshop.rmshop.repository.RestockLogRepository;
import com.rmshop.rmshop.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final SaleRepository saleRepository;
    private final RestockLogRepository restockLogRepository;
    private final ExpenditureRepository expenditureRepository;

    public ReportService(SaleRepository saleRepository, RestockLogRepository restockLogRepository, ExpenditureRepository expenditureRepository) {
        this.saleRepository = saleRepository;
        this.restockLogRepository = restockLogRepository;
        this.expenditureRepository = expenditureRepository;
    }

    public record RevenueSummary(BigDecimal revenue, BigDecimal restockCost, BigDecimal otherExpenditures, BigDecimal profit) {}
    public record EmployeePerformance(Long employeeId, String employeeName, BigDecimal totalSales, int itemsSold) {}
    public record RevenueProjection(BigDecimal averageDailyRevenue, int projectedDays, BigDecimal projectedRevenue) {}
    public record CashReconciliation(LocalDate date, BigDecimal expectedCash, BigDecimal actualCash, BigDecimal discrepancy) {}
    public record ProductDemand(Long productId, String productName, int quantitySold) {}

    // FR: revenue minus restock spend and other expenditures over an inclusive date range
    public RevenueSummary getRevenueSummary(LocalDate start, LocalDate end) {
        BigDecimal revenue = sumRevenue(salesBetween(start, end));
        BigDecimal restockCost = restockLogRepository.findByRestockedAtBetween(startOf(start), endOf(end)).stream()
                .map(RestockLog::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otherExpenditures = expenditureRepository.findByExpenseDateBetween(start, end).stream()
                .map(Expenditure::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RevenueSummary(revenue, restockCost, otherExpenditures, revenue.subtract(restockCost).subtract(otherExpenditures));
    }

    public Optional<EmployeePerformance> getTopEmployee(LocalDate start, LocalDate end) {
        Map<Long, EmployeePerformance> byEmployee = new LinkedHashMap<>();
        for (Sale sale : salesBetween(start, end)) {
            int items = sale.getItems().stream().mapToInt(SaleItem::getQuantity).sum();
            byEmployee.merge(sale.getEmployee().getId(),
                    new EmployeePerformance(sale.getEmployee().getId(), sale.getEmployee().getFullName(), sale.getTotalAmount(), items),
                    (a, b) -> new EmployeePerformance(a.employeeId(), a.employeeName(), a.totalSales().add(b.totalSales()), a.itemsSold() + b.itemsSold()));
        }
        return byEmployee.values().stream().max(Comparator.comparing(EmployeePerformance::totalSales));
    }

    // Straight-line projection: average daily revenue across the range, times the days ahead
    public RevenueProjection getProjectedRevenue(LocalDate start, LocalDate end, int projectedDays) {
        long days = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
        BigDecimal average = sumRevenue(salesBetween(start, end)).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        return new RevenueProjection(average, projectedDays, average.multiply(BigDecimal.valueOf(projectedDays)));
    }

    // Positive discrepancy = drawer is over, negative = drawer is short
    public CashReconciliation reconcile(LocalDate date, BigDecimal actualCash) {
        BigDecimal expected = sumRevenue(salesBetween(date, date));
        return new CashReconciliation(date, expected, actualCash, actualCash.subtract(expected));
    }

    public List<ProductDemand> getMostWantedProducts(LocalDate start, LocalDate end) {
        Map<Long, ProductDemand> byProduct = new LinkedHashMap<>();
        for (Sale sale : salesBetween(start, end)) {
            for (SaleItem item : sale.getItems()) {
                byProduct.merge(item.getProduct().getId(),
                        new ProductDemand(item.getProduct().getId(), item.getProduct().getName(), item.getQuantity()),
                        (a, b) -> new ProductDemand(a.productId(), a.productName(), a.quantitySold() + b.quantitySold()));
            }
        }
        return byProduct.values().stream()
                .sorted(Comparator.comparingInt(ProductDemand::quantitySold).reversed())
                .toList();
    }

    private List<Sale> salesBetween(LocalDate start, LocalDate end) {
        return saleRepository.findBySaleDateBetween(startOf(start), endOf(end));
    }

    private static BigDecimal sumRevenue(List<Sale> sales) {
        return sales.stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    private static LocalDateTime endOf(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }
}
