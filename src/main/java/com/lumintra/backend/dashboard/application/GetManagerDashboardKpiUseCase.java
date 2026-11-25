package com.lumintra.backend.dashboard.application;

import com.lumintra.backend.dashboard.api.request.ManagerDashboardFilterRequest;
import com.lumintra.backend.dashboard.api.response.ManagerKpiOverviewResponse;
import com.lumintra.backend.dashboard.infrastructure.caching.DashboardCacheNames;
import com.lumintra.backend.dashboard.infrastructure.persistence.mapper.ManagerDashboardMapper;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.CategoryRevenueRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.DailyRevenueRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.LowStockLedRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.ManagerDashboardAnalyticsRepository;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.TopProductRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.WarrantyAlertRow;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class GetManagerDashboardKpiUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetManagerDashboardKpiUseCase.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("MANAGER", "ADMIN");
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long LOW_STOCK_THRESHOLD = 10L;
    private static final int TOP_PRODUCTS_LIMIT = 5;
    private static final int WARRANTY_ALERTS_LIMIT = 5;

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final ManagerDashboardAnalyticsRepository analyticsRepository;
    private final ManagerDashboardMapper mapper;
    @Nullable
    private final CacheManager cacheManager;

    public GetManagerDashboardKpiUseCase(
            InternalEmployeeAccessService internalEmployeeAccessService,
            ManagerDashboardAnalyticsRepository analyticsRepository,
            ManagerDashboardMapper mapper) {
        this(internalEmployeeAccessService, analyticsRepository, mapper, null);
    }

    @Autowired
    public GetManagerDashboardKpiUseCase(
            InternalEmployeeAccessService internalEmployeeAccessService,
            ManagerDashboardAnalyticsRepository analyticsRepository,
            ManagerDashboardMapper mapper,
            @Nullable CacheManager cacheManager) {
        this.internalEmployeeAccessService = internalEmployeeAccessService;
        this.analyticsRepository = analyticsRepository;
        this.mapper = mapper;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    public ManagerKpiOverviewResponse execute(String subject, ManagerDashboardFilterRequest request) {
        return execute(subject, request, false);
    }

    @Transactional(readOnly = true)
    public ManagerKpiOverviewResponse execute(String subject, ManagerDashboardFilterRequest request, boolean refresh) {
        // 1. Anti-stale-JWT verification: ALWAYS verify in DB before any cache lookup so suspended employees cannot hit cache
        internalEmployeeAccessService.requireActiveEmployeeAccountId(subject, ALLOWED_ROLES);

        String cacheKey = request.month() + ":" + request.compareWith();
        if (!refresh) {
            ManagerKpiOverviewResponse cached = readFromCache(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        // 2. Parse reporting periods with Month-To-Date (MTD) like-for-like logic in Asia/Ho_Chi_Minh timezone
        YearMonth currentYm = YearMonth.parse(request.month());
        YearMonth compareYm = YearMonth.parse(request.compareWith());

        LocalDate today = LocalDate.now(REPORTING_ZONE);
        YearMonth thisYm = YearMonth.from(today);

        Instant startCurrent;
        Instant endCurrent;
        Instant startCompare;
        Instant endCompare;
        String revenuePeriod;

        if (currentYm.equals(thisYm)) {
            int currentDay = today.getDayOfMonth();
            startCurrent = currentYm.atDay(1).atStartOfDay(REPORTING_ZONE).toInstant();
            endCurrent = today.atTime(LocalTime.MAX).atZone(REPORTING_ZONE).toInstant();

            int compareMaxDay = Math.min(currentDay, compareYm.lengthOfMonth());
            startCompare = compareYm.atDay(1).atStartOfDay(REPORTING_ZONE).toInstant();
            endCompare = compareYm.atDay(compareMaxDay).atTime(LocalTime.MAX).atZone(REPORTING_ZONE).toInstant();

            revenuePeriod = "vs. cùng kỳ tháng trước (" + currentDay + " ngày)";
        } else {
            startCurrent = currentYm.atDay(1).atStartOfDay(REPORTING_ZONE).toInstant();
            endCurrent = currentYm.atEndOfMonth().atTime(LocalTime.MAX).atZone(REPORTING_ZONE).toInstant();

            startCompare = compareYm.atDay(1).atStartOfDay(REPORTING_ZONE).toInstant();
            endCompare = compareYm.atEndOfMonth().atTime(LocalTime.MAX).atZone(REPORTING_ZONE).toInstant();

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

        // 7. Query top 5 selling products and their comparison-period units for trend calculation
        List<TopProductRow> topProductRows = analyticsRepository.findTopSellingProducts(startCurrent, endCurrent, TOP_PRODUCTS_LIMIT);
        List<Long> topProductIds = topProductRows.stream()
                .map(TopProductRow::productId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Long> previousProductUnits = topProductIds.isEmpty()
                ? Collections.emptyMap()
                : analyticsRepository.findProductUnitsSoldMap(startCompare, endCompare, topProductIds);

        List<DailyRevenueRow> dailyRevenueRows = analyticsRepository.findDailyRevenue(startCurrent, endCurrent);

        log.info("Manager KPI overview retrieved [month={}] [compareWith={}] [refresh={}] [totalRevenue={}] [growth={}%]",
                request.month(), request.compareWith(), refresh, totalCurrentRev, growthPercent);

        // 8. Map to response and update cache
        ManagerKpiOverviewResponse response = mapper.toResponse(
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
                warrantyAlerts,
                LOW_STOCK_THRESHOLD,
                previousProductUnits,
                currentYm,
                dailyRevenueRows
        );
        writeToCache(cacheKey, response);
        return response;
    }

    @Nullable
    private ManagerKpiOverviewResponse readFromCache(String key) {
        if (cacheManager == null) {
            return null;
        }
        try {
            Cache cache = cacheManager.getCache(DashboardCacheNames.MANAGER_KPI);
            if (cache == null) {
                return null;
            }
            ManagerKpiOverviewResponse cached = cache.get(key, ManagerKpiOverviewResponse.class);
            if (cached != null && cached.dailyRevenue() == null) {
                return null;
            }
            return cached;
        } catch (RuntimeException ex) {
            log.warn("Dashboard KPI cache GET degraded to database [key={}] [error={}]", key, ex.getMessage());
            return null;
        }
    }

    private void writeToCache(String key, ManagerKpiOverviewResponse response) {
        if (cacheManager == null || response == null) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(DashboardCacheNames.MANAGER_KPI);
            if (cache != null) {
                cache.put(key, response);
            }
        } catch (RuntimeException ex) {
            log.warn("Dashboard KPI cache PUT skipped [key={}] [error={}]", key, ex.getMessage());
        }
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
