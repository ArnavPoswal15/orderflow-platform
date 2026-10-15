package com.shop.acceptance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public final class JdbcTestSupport {

    private JdbcTestSupport() {}

    public static int countOrders(String ordersJdbcUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(ordersJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM orders")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static int countOrdersByIdempotencyKey(String ordersJdbcUrl, String idempotencyKey) throws Exception {
        try (Connection c = DriverManager.getConnection(ordersJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM orders WHERE idempotency_key = ?")) {
            ps.setString(1, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static int countSucceededPayments(String paymentsJdbcUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(paymentsJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM payments WHERE status = 'SUCCEEDED'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static int countOrdersByStatus(String jdbcUrl, String status) throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM orders WHERE status = ?")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static int availableStock(String inventoryJdbcUrl, String sku) throws Exception {
        try (Connection c = DriverManager.getConnection(inventoryJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("SELECT available FROM inventory WHERE sku = ?")) {
            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static void setStock(String inventoryJdbcUrl, String sku, int available) throws Exception {
        try (Connection c = DriverManager.getConnection(inventoryJdbcUrl, "shop", "shop");
                PreparedStatement ps =
                        c.prepareStatement("UPDATE inventory SET available = ? WHERE sku = ?")) {
            ps.setInt(1, available);
            ps.setString(2, sku);
            ps.executeUpdate();
        }
    }

    public static String paymentStatus(String paymentsJdbcUrl, UUID orderId) throws Exception {
        try (Connection c = DriverManager.getConnection(paymentsJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("SELECT status FROM payments WHERE order_id = ?")) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString(1);
            }
        }
    }

    public static String reservationStatus(String inventoryJdbcUrl, UUID orderId) throws Exception {
        try (Connection c = DriverManager.getConnection(inventoryJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("SELECT status FROM reservations WHERE order_id = ?")) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString(1);
            }
        }
    }

    public static int unpublishedOutboxCountForOrder(String ordersJdbcUrl, UUID orderId) throws Exception {
        try (Connection c = DriverManager.getConnection(ordersJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM outbox WHERE published_at IS NULL AND payload::text LIKE ?")) {
            ps.setString(1, "%" + orderId + "%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static int unpublishedOutboxCount(String ordersJdbcUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(ordersJdbcUrl, "shop", "shop");
                PreparedStatement ps =
                        c.prepareStatement("SELECT count(*) FROM outbox WHERE published_at IS NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static boolean orderExists(String ordersJdbcUrl, UUID orderId) throws Exception {
        try (Connection c = DriverManager.getConnection(ordersJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("SELECT 1 FROM orders WHERE id = ?")) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void insertPendingOrder(String ordersJdbcUrl, UUID orderId) throws Exception {
        try (Connection c = DriverManager.getConnection(ordersJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO orders(id, idempotency_key, request_hash, status, total_cents, payment_token,
                            version, created_at, updated_at)
                        VALUES (?, ?, 'hash', 'PENDING', 399, 'ok', 0, now(), now())
                        """)) {
            ps.setObject(1, orderId);
            ps.setString(2, "pending-" + orderId);
            ps.executeUpdate();
        }
        try (Connection c = DriverManager.getConnection(ordersJdbcUrl, "shop", "shop");
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO order_items(order_id, sku, qty, unit_price_cents)
                        VALUES (?, 'MILK', 1, 399)
                        """)) {
            ps.setObject(1, orderId);
            ps.executeUpdate();
        }
    }
}
