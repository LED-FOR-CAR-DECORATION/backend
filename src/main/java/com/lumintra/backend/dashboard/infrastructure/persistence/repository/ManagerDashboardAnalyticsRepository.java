package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                        WHEN c.status = 'AWAITING_EXTRA_PAYMENT' THEN c.initial_price 
                        ELSE c.total_price 
                    END
                ), 0)
                FROM commission c
                WHERE c.status NOT IN ('PENDING_REVIEW', 'PENDING_PAYMENT', 'CANCELLED', 'EXPIRED')
                  AND c.created_at >= :start AND c.created_at <= :end
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));
        Object result = query.getSingleResult();
        return toBigDecimal(result);
    }

    @SuppressWarnings("unchecked")
    public List<DailyRevenueRow> findDailyRevenue(Instant start, Instant end) {
        String sql = """
                SELECT
                    day_num,
                    COALESCE(SUM(rev), 0) AS total_rev,
                    COALESCE(SUM(cnt), 0) AS total_cnt
                FROM (
                    SELECT
                        CAST(EXTRACT(DAY FROM (o.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')) AS INTEGER) AS day_num,
                        o.total_amount AS rev,
                        1 AS cnt
                    FROM "order" o
                    WHERE o.status NOT IN ('PENDING_PAYMENT', 'DELIVERY_FAILED', 'REFUNDED', 'CANCELED')
                      AND o.created_at >= :start AND o.created_at <= :end
                    UNION ALL
                    SELECT
                        CAST(EXTRACT(DAY FROM (c.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')) AS INTEGER) AS day_num,
                        CASE
                            WHEN c.status = 'AWAITING_EXTRA_PAYMENT' THEN c.initial_price
                            ELSE c.total_price
                        END AS rev,
                        1 AS cnt
                    FROM commission c
                    WHERE c.status NOT IN ('PENDING_REVIEW', 'PENDING_PAYMENT', 'CANCELLED', 'EXPIRED')
                      AND c.created_at >= :start AND c.created_at <= :end
                ) combined
                GROUP BY day_num
                ORDER BY day_num ASC
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));
        List<Object[]> rows = query.getResultList();
        List<DailyRevenueRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            int day = ((Number) row[0]).intValue();
            result.add(new DailyRevenueRow(day, toBigDecimal(row[1]), toLong(row[2])));
        }
        return result;
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
        String sql = "SELECT COUNT(p.product_id) FROM product p WHERE p.product_type = 'BUNDLE' AND p.status = 'PUBLISHED'";
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
                    p.base_price,
                    GREATEST(0, COALESCE((
                        SELECT SUM(
                            CASE
                                WHEN p_o.product_type = 'LED' THEN oi.quantity
                                ELSE oi.quantity * bi.quantity
                            END
                        )
                        FROM order_item oi
                        JOIN "order" o ON o.order_id = oi.order_id
                        JOIN product p_o ON p_o.product_id = oi.product_id
                        LEFT JOIN led_model lm_o ON lm_o.product_id = p_o.product_id
                        LEFT JOIN bundle b_o ON b_o.product_id = p_o.product_id
                        LEFT JOIN bundle_item bi ON bi.bundle_id = b_o.bundle_id AND bi.item_type = 'LED' AND bi.model_id = m.model_id
                        WHERE o.status IN ('PENDING_PAYMENT', 'PROCESSING', 'PARTIALLY_FULFILLED')
                          AND (
                              (p_o.product_type = 'LED' AND lm_o.model_id = m.model_id)
                              OR (p_o.product_type = 'BUNDLE' AND bi.model_id = m.model_id)
                          )
                    ), 0) - COALESCE((
                        SELECT COUNT(oif.fulfillment_id)
                        FROM order_item_fulfillment oif
                        JOIN order_item oi2 ON oi2.order_item_id = oif.order_item_id
                        JOIN "order" o2 ON o2.order_id = oi2.order_id
                        JOIN led_instance li2 ON li2.instance_id = oif.instance_id
                        WHERE o2.status IN ('PENDING_PAYMENT', 'PROCESSING', 'PARTIALLY_FULFILLED')
                          AND li2.model_id = m.model_id
                    ), 0)) as reserved_qty
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
                    toBigDecimal(row[6]),
                    toLong(row[7])
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
                            WHEN c.status = 'AWAITING_EXTRA_PAYMENT' THEN c.initial_price 
                            ELSE c.total_price 
                        END
                    ), 0) as rev,
                    COUNT(c.commission_id) as units
                FROM commission c
                WHERE c.status NOT IN ('PENDING_REVIEW', 'PENDING_PAYMENT', 'CANCELLED', 'EXPIRED')
                  AND c.created_at >= :start AND c.created_at <= :end
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
                    COALESCE(SUM(oi.subtotal), 0) as total_rev,
                    MAX(p.status) as product_status,
                    CASE
                        WHEN p.product_type = 'LED' THEN MAX(lm.stock_quantity)
                        WHEN p.product_type = 'BUNDLE' THEN MIN(bs.min_bundle_units)
                        ELSE NULL
                    END as available_stock,
                    MAX(lm.status) as led_model_status
                FROM order_item oi
                JOIN "order" o ON o.order_id = oi.order_id
                JOIN product p ON p.product_id = oi.product_id
                LEFT JOIN led_model lm ON lm.product_id = p.product_id
                LEFT JOIN bundle b ON b.product_id = p.product_id
                LEFT JOIN (
                    SELECT
                        bi.bundle_id,
                        CAST(MIN(
                            CASE
                                WHEN bi.quantity > 0 THEN COALESCE(lm_b.stock_quantity, 0) / bi.quantity
                                ELSE 0
                            END
                        ) AS BIGINT) AS min_bundle_units
                    FROM bundle_item bi
                    JOIN led_model lm_b ON lm_b.model_id = bi.model_id
                    WHERE bi.item_type = 'LED'
                    GROUP BY bi.bundle_id
                ) bs ON bs.bundle_id = b.bundle_id
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
                    toBigDecimal(row[5]),
                    toString(row[6]),
                    row[7] != null ? toLong(row[7]) : null,
                    toString(row[8])
            ));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public Map<Long, Long> findProductUnitsSoldMap(Instant start, Instant end, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String sql = """
                SELECT
                    oi.product_id,
                    COALESCE(SUM(oi.quantity), 0) as total_units
                FROM order_item oi
                JOIN "order" o ON o.order_id = oi.order_id
                WHERE o.status NOT IN ('PENDING_PAYMENT', 'DELIVERY_FAILED', 'REFUNDED', 'CANCELED')
                  AND o.created_at >= :start AND o.created_at <= :end
                  AND oi.product_id IN :productIds
                GROUP BY oi.product_id
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));
        query.setParameter("productIds", productIds);
        List<Object[]> rows = query.getResultList();

        Map<Long, Long> unitsByProductId = new LinkedHashMap<>();
        for (Object[] row : rows) {
            unitsByProductId.put(toLong(row[0]), toLong(row[1]));
        }
        return unitsByProductId;
    }

    @SuppressWarnings("unchecked")
    public List<RevenueSourceRow> findOrderItemRevenueSources(
            Instant start,
            Instant end,
            String productType,
            Long productId) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    o.order_id,
                    c.full_name,
                    ua.email,
                    o.status,
                    o.order_type,
                    p.product_id,
                    p.product_name,
                    p.product_type,
                    oi.quantity,
                    oi.unit_price,
                    oi.subtotal,
                    o.total_amount,
                    o.created_at
                FROM order_item oi
                JOIN "order" o ON o.order_id = oi.order_id
                JOIN product p ON p.product_id = oi.product_id
                LEFT JOIN customer c ON c.account_id = o.customer_id
                LEFT JOIN user_account ua ON ua.account_id = o.customer_id
                WHERE o.status NOT IN ('PENDING_PAYMENT', 'DELIVERY_FAILED', 'REFUNDED', 'CANCELED')
                  AND o.created_at >= :start AND o.created_at <= :end
                """);
        if (productType != null && !productType.isBlank()) {
            sql.append(" AND p.product_type = :productType");
        }
        if (productId != null) {
            sql.append(" AND oi.product_id = :productId");
        }
        sql.append(" ORDER BY o.created_at DESC, o.order_id DESC, oi.order_item_id ASC");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));
        if (productType != null && !productType.isBlank()) {
            query.setParameter("productType", productType);
        }
        if (productId != null) {
            query.setParameter("productId", productId);
        }

        List<Object[]> rows = query.getResultList();
        List<RevenueSourceRow> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            results.add(new RevenueSourceRow(
                    "ORDER",
                    toLong(row[0]),
                    toString(row[1]),
                    toString(row[2]),
                    toString(row[3]),
                    toString(row[4]),
                    row[5] != null ? toLong(row[5]) : null,
                    toString(row[6]),
                    toString(row[7]),
                    toLong(row[8]),
                    toBigDecimal(row[9]),
                    toBigDecimal(row[10]),
                    toBigDecimal(row[11]),
                    toInstant(row[12])
            ));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public List<RevenueSourceRow> findOrderSummaryRevenueSources(Instant start, Instant end) {
        String sql = """
                SELECT
                    o.order_id,
                    c.full_name,
                    ua.email,
                    o.status,
                    o.order_type,
                    COALESCE(SUM(oi.quantity), 0) as total_units,
                    COUNT(oi.order_item_id) as item_count,
                    MIN(p.product_name) as first_product_name,
                    MIN(p.product_type) as first_product_type,
                    COUNT(DISTINCT p.product_type) as distinct_types,
                    o.total_amount,
                    o.created_at
                FROM "order" o
                LEFT JOIN order_item oi ON oi.order_id = o.order_id
                LEFT JOIN product p ON p.product_id = oi.product_id
                LEFT JOIN customer c ON c.account_id = o.customer_id
                LEFT JOIN user_account ua ON ua.account_id = o.customer_id
                WHERE o.status NOT IN ('PENDING_PAYMENT', 'DELIVERY_FAILED', 'REFUNDED', 'CANCELED')
                  AND o.created_at >= :start AND o.created_at <= :end
                GROUP BY o.order_id, c.full_name, ua.email, o.status, o.order_type, o.total_amount, o.created_at
                ORDER BY o.created_at DESC, o.order_id DESC
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));

        List<Object[]> rows = query.getResultList();
        List<RevenueSourceRow> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long itemCount = toLong(row[6]);
            String firstName = toString(row[7]);
            String summaryName = firstName != null ? firstName : ("Đơn hàng #" + toLong(row[0]));
            if (itemCount > 1) {
                summaryName = summaryName + " (+" + (itemCount - 1) + " sản phẩm khác)";
            }
            long distinctTypes = toLong(row[9]);
            String productType = distinctTypes > 1 ? "MIXED" : toString(row[8]);
            BigDecimal totalAmount = toBigDecimal(row[10]);

            results.add(new RevenueSourceRow(
                    "ORDER",
                    toLong(row[0]),
                    toString(row[1]),
                    toString(row[2]),
                    toString(row[3]),
                    toString(row[4]),
                    null,
                    summaryName,
                    productType,
                    toLong(row[5]),
                    totalAmount,
                    totalAmount,
                    totalAmount,
                    toInstant(row[11])
            ));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public List<RevenueSourceRow> findCommissionRevenueSources(Instant start, Instant end) {
        String sql = """
                SELECT
                    c.commission_id,
                    cu.full_name,
                    ua.email,
                    c.status,
                    c.title,
                    CASE
                        WHEN c.status = 'AWAITING_EXTRA_PAYMENT' THEN c.initial_price
                        ELSE c.total_price
                    END as recognized_rev,
                    c.total_price,
                    c.created_at
                FROM commission c
                LEFT JOIN customer cu ON cu.account_id = c.customer_id
                LEFT JOIN user_account ua ON ua.account_id = c.customer_id
                WHERE c.status NOT IN ('PENDING_REVIEW', 'PENDING_PAYMENT', 'CANCELLED', 'EXPIRED')
                  AND c.created_at >= :start AND c.created_at <= :end
                ORDER BY c.created_at DESC, c.commission_id DESC
                """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", Timestamp.from(start));
        query.setParameter("end", Timestamp.from(end));

        List<Object[]> rows = query.getResultList();
        List<RevenueSourceRow> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            BigDecimal recognized = toBigDecimal(row[5]);
            BigDecimal totalPrice = toBigDecimal(row[6]);
            results.add(new RevenueSourceRow(
                    "COMMISSION",
                    toLong(row[0]),
                    toString(row[1]),
                    toString(row[2]),
                    toString(row[3]),
                    "COMMISSION",
                    null,
                    toString(row[4]),
                    "COMMISSION",
                    1L,
                    recognized,
                    recognized,
                    totalPrice,
                    toInstant(row[7])
            ));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public String findProductNameById(Long productId) {
        if (productId == null) {
            return null;
        }
        List<Object> rows = entityManager.createNativeQuery(
                        "SELECT p.product_name FROM product p WHERE p.product_id = :productId")
                .setParameter("productId", productId)
                .getResultList();
        return rows.isEmpty() ? null : toString(rows.get(0));
    }

    @SuppressWarnings("unchecked")
    public OrderAuditHeaderRow findOrderAuditHeader(Long orderId) {
        if (orderId == null) {
            return null;
        }
        String sql = """
                SELECT
                    o.order_id,
                    c.full_name,
                    ua.email,
                    o.status,
                    o.order_type,
                    o.subtotal,
                    o.shipping_fee,
                    o.total_amount,
                    o.created_at,
                    pt.transaction_code,
                    pt.gateway,
                    pt.status as payment_status,
                    pt.paid_at
                FROM "order" o
                LEFT JOIN customer c ON c.account_id = o.customer_id
                LEFT JOIN user_account ua ON ua.account_id = o.customer_id
                LEFT JOIN (
                    SELECT pt1.order_id, pt1.transaction_code, pt1.gateway, pt1.status, pt1.paid_at
                    FROM payment_transaction pt1
                    WHERE pt1.payment_id = (
                        SELECT MAX(pt2.payment_id)
                        FROM payment_transaction pt2
                        WHERE pt2.order_id = pt1.order_id
                    )
                ) pt ON pt.order_id = o.order_id
                WHERE o.order_id = :orderId
                """;
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("orderId", orderId)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        return new OrderAuditHeaderRow(
                toLong(row[0]),
                toString(row[1]),
                toString(row[2]),
                toString(row[3]),
                toString(row[4]),
                toBigDecimal(row[5]),
                toBigDecimal(row[6]),
                toBigDecimal(row[7]),
                toInstant(row[8]),
                toString(row[9]),
                toString(row[10]),
                toString(row[11]),
                toInstant(row[12])
        );
    }

    @SuppressWarnings("unchecked")
    public List<OrderAuditLineRow> findOrderAuditLines(Long orderId) {
        if (orderId == null) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT
                    oi.order_item_id,
                    p.product_id,
                    p.product_name,
                    p.product_type,
                    oi.quantity,
                    oi.unit_price,
                    oi.subtotal
                FROM order_item oi
                JOIN product p ON p.product_id = oi.product_id
                WHERE oi.order_id = :orderId
                ORDER BY oi.order_item_id ASC
                """;
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("orderId", orderId)
                .getResultList();
        List<OrderAuditLineRow> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            results.add(new OrderAuditLineRow(
                    toLong(row[0]),
                    toLong(row[1]),
                    toString(row[2]),
                    toString(row[3]),
                    toLong(row[4]),
                    toBigDecimal(row[5]),
                    toBigDecimal(row[6])
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
