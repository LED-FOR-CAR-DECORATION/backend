package com.lumintra.backend.dashboard.application;

import com.lumintra.backend.dashboard.api.request.ManagerRevenueDrilldownRequest;
import com.lumintra.backend.dashboard.api.response.ManagerOrderAuditDetailResponse;
import com.lumintra.backend.dashboard.api.response.ManagerRevenueDrilldownResponse;
import com.lumintra.backend.dashboard.api.response.ManagerRevenueDrilldownResponse.RevenueSourceItem;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.ManagerDashboardAnalyticsRepository;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.OrderAuditHeaderRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.OrderAuditLineRow;
import com.lumintra.backend.dashboard.infrastructure.persistence.repository.RevenueSourceRow;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetManagerRevenueDrilldownUseCase {

    private static final Set<String> ALLOWED_ROLES = Set.of("MANAGER", "ADMIN");
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final ManagerDashboardAnalyticsRepository analyticsRepository;

    @Transactional(readOnly = true)
    public ManagerRevenueDrilldownResponse execute(String subject, ManagerRevenueDrilldownRequest request) {
        internalEmployeeAccessService.requireActiveEmployeeAccountId(subject, ALLOWED_ROLES);

        YearMonth currentYm = YearMonth.parse(request.month());
        LocalDate today = LocalDate.now(REPORTING_ZONE);
        YearMonth thisYm = YearMonth.from(today);

        Instant startCurrent = currentYm.atDay(1).atStartOfDay(REPORTING_ZONE).toInstant();
        Instant endCurrent = currentYm.equals(thisYm)
                ? today.atTime(LocalTime.MAX).atZone(REPORTING_ZONE).toInstant()
                : currentYm.atEndOfMonth().atTime(LocalTime.MAX).atZone(REPORTING_ZONE).toInstant();

        String scopeType;
        String scopeLabel;
        String resolvedCategory = request.category();
        Long productId = request.productId();
        List<RevenueSourceRow> rows = new ArrayList<>();

        if (productId != null) {
            scopeType = "PRODUCT";
            rows.addAll(analyticsRepository.findOrderItemRevenueSources(startCurrent, endCurrent, null, productId));
            String resolvedName = !rows.isEmpty() && rows.get(0).itemName() != null
                    ? rows.get(0).itemName()
                    : analyticsRepository.findProductNameById(productId);
            scopeLabel = resolvedName != null ? resolvedName : ("Sản phẩm #" + productId);
            if (!rows.isEmpty()) {
                resolvedCategory = mapProductTypeToCategory(rows.get(0).productType());
            }
        } else if (resolvedCategory == null || "ALL".equals(resolvedCategory)) {
            scopeType = "ALL";
            scopeLabel = "Tổng doanh thu";
            resolvedCategory = "ALL";
            rows.addAll(analyticsRepository.findOrderSummaryRevenueSources(startCurrent, endCurrent));
            rows.addAll(analyticsRepository.findCommissionRevenueSources(startCurrent, endCurrent));
            rows.sort(Comparator.comparing(
                    RevenueSourceRow::createdAt,
                    Comparator.nullsLast(Comparator.reverseOrder())
            ).thenComparing(RevenueSourceRow::referenceId, Comparator.nullsLast(Comparator.reverseOrder())));
        } else if ("COMMISSION".equals(resolvedCategory)) {
            scopeType = "CATEGORY";
            scopeLabel = categoryLabel("COMMISSION");
            rows.addAll(analyticsRepository.findCommissionRevenueSources(startCurrent, endCurrent));
        } else {
            scopeType = "CATEGORY";
            scopeLabel = categoryLabel(resolvedCategory);
            String productType = mapCategoryToProductType(resolvedCategory);
            rows.addAll(analyticsRepository.findOrderItemRevenueSources(startCurrent, endCurrent, productType, null));
        }

        List<RevenueSourceItem> items = rows.stream()
                .map(this::toItem)
                .toList();

        BigDecimal totalRevenue = items.stream()
                .map(RevenueSourceItem::contributionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalUnits = items.stream()
                .mapToLong(RevenueSourceItem::quantity)
                .sum();

        long distinctSources = items.stream()
                .map(item -> item.sourceType() + ":" + item.referenceId())
                .distinct()
                .count();

        return new ManagerRevenueDrilldownResponse(
                request.month(),
                scopeType,
                scopeLabel,
                resolvedCategory,
                productId,
                startCurrent,
                endCurrent,
                totalRevenue,
                totalUnits,
                distinctSources,
                items
        );
    }

    @Transactional(readOnly = true)
    public ManagerOrderAuditDetailResponse getOrderAuditDetail(String subject, Long orderId) {
        internalEmployeeAccessService.requireActiveEmployeeAccountId(subject, ALLOWED_ROLES);

        OrderAuditHeaderRow header = analyticsRepository.findOrderAuditHeader(orderId);
        if (header == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        List<OrderAuditLineRow> lines = analyticsRepository.findOrderAuditLines(orderId);
        List<ManagerOrderAuditDetailResponse.OrderLineItem> items = lines.stream()
                .map(line -> new ManagerOrderAuditDetailResponse.OrderLineItem(
                        line.orderItemId(),
                        line.productId(),
                        line.productName(),
                        mapProductTypeToCategory(line.productType()),
                        line.quantity(),
                        line.unitPrice(),
                        line.subtotal()
                ))
                .toList();

        return new ManagerOrderAuditDetailResponse(
                header.orderId(),
                "#" + header.orderId(),
                header.customerName(),
                header.customerEmail(),
                header.status(),
                header.orderType(),
                header.subtotal(),
                header.shippingFee(),
                header.totalAmount(),
                header.createdAt(),
                header.paymentCode(),
                header.paymentGateway(),
                header.paymentStatus(),
                header.paidAt(),
                items
        );
    }

    private RevenueSourceItem toItem(RevenueSourceRow row) {
        String prefix = "COMMISSION".equals(row.sourceType()) ? "COM-" : "#";
        return new RevenueSourceItem(
                row.sourceType(),
                row.referenceId(),
                prefix + row.referenceId(),
                row.customerName(),
                row.customerEmail(),
                row.status(),
                row.orderType(),
                row.productId(),
                row.itemName(),
                mapProductTypeToCategory(row.productType()),
                row.quantity(),
                row.unitPrice(),
                row.contributionAmount(),
                row.orderTotalAmount(),
                row.createdAt()
        );
    }

    private String mapCategoryToProductType(String category) {
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "HARDWARE" -> "LED";
            case "BUNDLE" -> "BUNDLE";
            case "CHARACTER" -> "CHARACTER";
            case "SUBSCRIPTION" -> "SUBSCRIPTION";
            default -> category;
        };
    }

    private String mapProductTypeToCategory(String productType) {
        if (productType == null) {
            return "MIXED";
        }
        return switch (productType.toUpperCase(Locale.ROOT)) {
            case "LED" -> "HARDWARE";
            case "BUNDLE" -> "BUNDLE";
            case "CHARACTER" -> "CHARACTER";
            case "SUBSCRIPTION" -> "SUBSCRIPTION";
            case "COMMISSION" -> "COMMISSION";
            default -> "MIXED";
        };
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "HARDWARE" -> "Phần cứng";
            case "COMMISSION" -> "Yêu cầu thiết kế";
            case "BUNDLE" -> "Gói sản phẩm";
            case "CHARACTER" -> "Nhân vật";
            case "SUBSCRIPTION" -> "Gói hội viên";
            default -> "Tổng doanh thu";
        };
    }
}
