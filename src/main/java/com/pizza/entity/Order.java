package com.pizza.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A placed customer order (US-007). Totals are computed by the service layer.
 * Default status on creation is {@code PLACED}.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

   @OneToMany(
    mappedBy = "order",
    cascade = CascadeType.ALL,
    orphanRemoval = true
)
@Builder.Default
private List<OrderItem> orderItems = new ArrayList<>();

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax", nullable = false, precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "coupon_code", length = 30)
    private String couponCode;

    @Column(name = "discount_percentage")
    private Integer discountPercentage;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "delivery_address", nullable = false, length = 255)
    private String deliveryAddress;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @org.hibernate.annotations.UpdateTimestamp

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    public void addOrderItem(OrderItem item) {
    orderItems.add(item);
    item.setOrder(this);
}

public void removeOrderItem(OrderItem item) {
    orderItems.remove(item);
    item.setOrder(null);
}
}
