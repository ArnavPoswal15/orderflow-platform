package com.shop.order.persistence;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "order_items")
@IdClass(OrderItemEntity.Pk.class)
public class OrderItemEntity {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Id
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private OrderEntity order;

    private int qty;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public void setOrder(OrderEntity order) {
        this.order = order;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }

    public void setUnitPriceCents(long unitPriceCents) {
        this.unitPriceCents = unitPriceCents;
    }

    public record Pk(UUID orderId, String sku) implements java.io.Serializable {}
}
