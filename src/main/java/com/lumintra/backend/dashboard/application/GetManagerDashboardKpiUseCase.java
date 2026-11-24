package com.lumintra.backend.dashboard.application;

import com.lumintra.backend.dashboard.api.request.ManagerDashboardFilterRequest;
import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse;
import com.lumintra.backend.dashboard.infrastructure.caching.DashboardCacheNames;
import com.lumintra.backend.dashboard.infrastructure.persistence.mapper.ManagerDashboardMapper;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.CategoryRevenueRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.LowStockLedRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.ManagerDashboardAnalyticsRepository;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.TopProductRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.WarrantyAlertRow;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetManagerDashboardKpiUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetManagerDashboardKpiUseCase.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("MANAGER", "ADMIN");
    private static final long LOW_STOCK_THRESHOLD = 10L;
    private static final int TOP_PRODUCTS_LIMIT = 5;
    private static final int WARRANTY_ALERTS_LIMIT = 5;

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final ManagerDashboardAnalyticsRepository analyticsRepository;
    private final ManagerDashboardMapper mapper;

    @Transactional(readOnly = true)
    @Cacheable(value = DashboardCacheNames.MANAGER_KPI, key = "#request.month() + ':' + #request.compareWith()", unless = "#result == null")
    public ManagerKpiOverviewResponse execute(String subject, ManagerDashboardFilterRequest request) {
        // 1. Anti-stale-JWT verification: Verify in DB that caller is an ACTIVE employee with MANAGER or ADMIN role
        internalEmployeeAccessService.requireActiveEmployeeAccountId(subject, ALLOWED_ROLES);

        // 2. Parse reporting periods with Month-To-Date (MTD) like-for-like logic
        YearMonth currentYm = YearMonth.parse(request.month());
        YearMonth compareYm = YearMonth.parse(request.compareWith());

        LocalDate today = LocalDate.now();
        YearMonth thisYm = YearMonth.from(today);

        Instant startCurrent;
        Instant endCurrent;
        Instant startCompare;
        Instant endCompare;
        String revenuePeriod;

        if (currentYm.equals(thisYm)) {
            int currentDay = today.getDayOfMonth();
            startCurrent = currentYm.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            endCurrent = today.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

            int compareMaxDay = Math.min(currentDay, compareYm.lengthOfMonth());
            startCompare = compareYm.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            endCompare = compareYm.atDay(compareMaxDay).atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

            revenuePeriod = "vs. cùng kỳ tháng trước (" + currentDay + " ngày)";
        } else {
            startCurrent = currentYm.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            endCurrent = currentYm.atEndOfMonth().atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

            startCompare = compareYm.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            endCompare = compareYm.atEndOfMonth().atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

            revenuePeriod = "vs. tháng " + request.compareWith();
        }

        // 3. Query revenue aggregations for current and comparison periods
        BigDecimal currentOrderRev = analyticsRepository.calculateOrderRevenue(startCurrent, endCurrent);
        BigDecimal currentCommRev = analyticsRepository.calculateCommissionRevenue(startCurrent, endCurrent);
        BigDecimal totalCurrentRev = currentOrderRev.add(currentCommRev);

        BigDecimal compareOrderRev = analyticsRepository.calculateOrderRevenue(startCompare, endCompare);
        BigDecimal compareCommRev = analyticsRepository.calculateCommissionRevenue(startCompare, endCompare);
        BigDecimal totalCompareRev = compareOrderRev.add(compareCommRev);

        double growthPercent = calculateGrowthPercent(totalCurrentRev, totalCompareRev);

        // 4. Query operational queues & catalog configurations
        long pendingCharacters = analyticsRepository.countPendingCharacters();
        long pendingCommissions = analyticsRepository.countPendingCommissions();
        long pendingWarrantyClaims = analyticsRepository.countPendingWarrantyClaims();
        List<WarrantyAlertRow> warrantyAlerts = analyticsRepository.findOpenWarrantyAlerts(WARRANTY_ALERTS_LIMIT);
        long activeBundles = analyticsRepository.countActiveBundles();
        long activeSubscriptions = analyticsRepository.countActiveSubscriptions();
        long activePriceConfigs = analyticsRepository.countActivePriceConfigs();

        // 5. Query inventory health & low stock hardware
        List<LowStockLedRow> lowStockModels = analyticsRepository.findLowStockModels(LOW_STOCK_THRESHOLD);
        long lowStockModelsCount = lowStockModels.size();

        // 6. Query category breakdown (5 categories)
        List<CategoryRevenueRow> orderCategoryRows = analyticsRepository.calculateOrderItemCategoryRevenues(startCurrent, endCurrent);
        CategoryRevenueRow commissionRow = analyticsRepository.calculateCommissionCategoryRevenue(startCurrent, endCurrent);

        // 7. Query top 5 selling products
        List<TopProductRow> topProductRows = analyticsRepository.findTopSellingProducts(startCurrent, endCurrent, TOP_PRODUCTS_LIMIT);

        log.info("Manager KPI overview retrieved [month={}] [compareWith={}] [totalRevenue={}] [growth={}%]",
                request.month(), request.compareWith(), totalCurrentRev, growthPercent);

        // 8. Map to response
        return mapper.toResponse(
                totalCurrentRev,
                growthPercent,
                revenuePeriod,
                pendingCharacters,
                pendingCommissions,
                pendingWarrantyClaims,
                lowStockModelsCount,
                activeBundles,
                activeSubscriptions,
                activePriceConfigs,
                orderCategoryRows,
                commissionRow,
                topProductRows,
                lowStockModels,
                warrantyAlerts
        );
    }

    private double calculateGrowthPercent(BigDecimal current, BigDecimal compare) {
        if (compare == null || compare.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        double currentVal = current != null ? current.doubleValue() : 0.0;
        double compareVal = compare.doubleValue();
        return ((currentVal - compareVal) / compareVal) * 100.0;
    }
}
