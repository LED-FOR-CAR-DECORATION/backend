package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ManagerDashboardAnalyticsRepository {

    private final EntityManager entityManager;

    public BigDecimal calculateOrderRevenue(Instant start, Instant end) {
        String sql = """
                SELECT COALESCE(SUM(o.total_amount), 0)
                FROM "order" o
                WHERE o.status NOT IN ('PENDING_PAYMENT', 'DELIVERY_FAILED', 'REFUNDED', 'CANCELED')
                  AND o.created_at >= :start AND o.created_at <= :end
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));
        Object result = query.getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal calculateCommissionRevenue(Instant start, Instant end) {
        String sql = """
                SELECT COALESCE(SUM(
                    CASE 
                        WHEN c.status = 'COMPLETED' THEN c.total_price 
                        ELSE c.initial_price 
                    END
                ), 0)
                FROM commission c
                WHERE c.status NOT IN ('PENDING_REVIEW', 'PENDING_PAYMENT', 'CANCELLED', 'EXPIRED')
                  AND c.updated_at >= :start AND c.updated_at <= :end
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));
        Object result = query.getSingleResult();
        return toBigDecimal(result);
    }

    public long countPendingCharacters() {
        String sql = "SELECT COUNT(p.product_id) FROM product p WHERE p.product_type = 'CHARACTER' AND p.status = 'UNPUBLISHED'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        return toLong(result);
    }

    public long countPendingCommissions() {
        String sql = "SELECT COUNT(c.commission_id) FROM commission c WHERE c.status = 'PENDING_REVIEW'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        return toLong(result);
    }

    public long countPendingWarrantyClaims() {
        String sql = "SELECT COUNT(w.ticket_id) FROM warranty_ticket w WHERE w.status = 'PENDING'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        return toLong(result);
    }

    @SuppressWarnings("unchecked")
    public List<WarrantyAlertRow> findOpenWarrantyAlerts(int limit) {
        String sql = """
                SELECT
                    w.ticket_id,
                    c.full_name,
                    ua.email,
                    m.model_name,
                    w.device_serial_number,
                    w.issue_description,
                    w.created_at,
                    w.status
                FROM warranty_ticket w
                LEFT JOIN customer c ON c.account_id = w.customer_id
                LEFT JOIN user_account ua ON ua.account_id = w.customer_id
                LEFT JOIN led_instance li ON li.serial_number = w.device_serial_number
                LEFT JOIN led_model m ON m.model_id = li.model_id
                WHERE w.status IN ('PENDING', 'APPROVED')
                ORDER BY CASE w.status WHEN 'PENDING' THEN 0 ELSE 1 END, w.created_at DESC
                LIMIT :limit
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("limit", limit);
        List<Object[]> rows = query.getResultList();

        List<WarrantyAlertRow> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(new WarrantyAlertRow(
                    toLong(row[0]),
                    toString(row[1]),
                    toString(row[2]),
                    toString(row[3]),
                    toString(row[4]),
                    toString(row[5]),
                    toInstant(row[6]),
                    toString(row[7])
            ));
        }
        return results;
    }

    public long countActiveBundles() {
        String sql = "SELECT COUNT(b.bundle_id) FROM bundle b WHERE b.status = 'ACTIVE'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        return toLong(result);
    }

    public long countActiveSubscriptions() {
        String sql = "SELECT COUNT(p.product_id) FROM product p WHERE p.product_type = 'SUBSCRIPTION' AND p.status = 'PUBLISHED'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        return toLong(result);
    }

    public long countActivePriceConfigs() {
        String sql = "SELECT COUNT(c.config_id) FROM commission_price_config c WHERE c.is_active = true";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        return toLong(result);
    }

    @SuppressWarnings("unchecked")
    public List<LowStockLedRow> findLowStockModels(long threshold) {
        String sql = """
                SELECT 
                    m.model_id,
                    m.product_id,
                    m.model_name,
                    m.width_px,
                    m.height_px,
                    m.stock_quantity,
                    p.base_price
                FROM led_model m
                LEFT JOIN product p ON p.product_id = m.product_id
                WHERE m.status != 'DEPRECATED'
                  AND m.stock_quantity < :threshold
                ORDER BY m.stock_quantity ASC
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("threshold", threshold);
        List<Object[]> rows = query.getResultList();

        List<LowStockLedRow> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(new LowStockLedRow(
                    toLong(row[0]),
                    row[1] != null ? toLong(row[1]) : null,
                    toString(row[2]),
                    toInteger(row[3]),
                    toInteger(row[4]),
                    toLong(row[5]),
                    toBigDecimal(row[6])
            ));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public List<CategoryRevenueRow> calculateOrderItemCategoryRevenues(Instant start, Instant end) {
        String sql = """
                SELECT 
                    p.product_type,
                    COALESCE(SUM(oi.subtotal), 0) as rev,
                    COALESCE(SUM(oi.quantity), 0) as units
                FROM order_item oi
                JOIN "order" o ON o.order_id = oi.order_id
                JOIN product p ON p.product_id = oi.product_id
                WHERE o.status NOT IN ('PENDING_PAYMENT', 'DELIVERY_FAILED', 'REFUNDED', 'CANCELED')
                  AND o.created_at >= :start AND o.created_at <= :end
                GROUP BY p.product_type
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));
        List<Object[]> rows = query.getResultList();

        List<CategoryRevenueRow> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(new CategoryRevenueRow(
                    toString(row[0]),
                    toBigDecimal(row[1]),
                    toLong(row[2])
            ));
        }
        return results;
    }

    public CategoryRevenueRow calculateCommissionCategoryRevenue(Instant start, Instant end) {
        String sql = """
                SELECT 
                    COALESCE(SUM(
                        CASE 
                            WHEN c.status = 'COMPLETED' THEN c.total_price 
                            ELSE c.initial_price 
                        END
                    ), 0) as rev,
                    COUNT(c.commission_id) as units
                FROM commission c
                WHERE c.status NOT IN ('PENDING_REVIEW', 'PENDING_PAYMENT', 'CANCELLED', 'EXPIRED')
                  AND c.updated_at >= :start AND c.updated_at <= :end
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));
        Object[] row = (Object[]) query.getSingleResult();
        return new CategoryRevenueRow(
                "COMMISSION",
                toBigDecimal(row[0]),
                toLong(row[1])
        );
    }

    @SuppressWarnings("unchecked")
    public List<TopProductRow> findTopSellingProducts(Instant start, Instant end, int limit) {
        String sql = """
                SELECT 
                    p.product_id,
                    p.product_name,
                    p.product_type,
                    p.thumbnail_url,
                    COALESCE(SUM(oi.quantity), 0) as total_units,
                    COALESCE(SUM(oi.subtotal), 0) as total_rev
                FROM order_item oi
                JOIN "order" o ON o.order_id = oi.order_id
                JOIN product p ON p.product_id = oi.product_id
                WHERE o.status NOT IN ('PENDING_PAYMENT', 'DELIVERY_FAILED', 'REFUNDED', 'CANCELED')
                  AND o.created_at >= :start AND o.created_at <= :end
                GROUP BY p.product_id, p.product_name, p.product_type, p.thumbnail_url
                ORDER BY total_units DESC, total_rev DESC
                LIMIT :limit
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));
        query.setParameter("limit", limit);
        List<Object[]> rows = query.getResultList();

        List<TopProductRow> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(new TopProductRow(
                    toLong(row[0]),
                    toString(row[1]),
                    toString(row[2]),
                    toString(row[3]),
                    toLong(row[4]),
                    toBigDecimal(row[5])
            ));
        }
        return results;
    }

    private static Long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number num) return num.longValue();
        return Long.parseLong(obj.toString());
    }

    private static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        return new BigDecimal(obj.toString());
    }

    private static Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.intValue();
        return Integer.parseInt(obj.toString());
    }

    private static Instant toInstant(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Instant i) return i;
        if (obj instanceof Timestamp ts) return ts.toInstant();
        if (obj instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(obj.toString());
    }

    private static String toString(Object obj) {
        return obj == null ? null : obj.toString();
    }
}
