package com.pizza.entity;

import java.util.Set;

/**
 * Valid values for {@link Order#getStatus()} and the admin-driven transitions
 * between them (US-018). Persisted as the enum name in the existing
 * {@code orders.status} varchar column — no schema change required.
 *
 * <p>Customer-facing code ({@code OrderService}, {@code OrderController}) still
 * works directly with the {@code PLACED}/{@code CANCELLED} string literals it
 * already used; this enum only governs the new admin status-update flow.</p>
 */
public enum OrderStatus {
    PLACED(Set.of("PROCESSING", "CANCELLED")),
    PROCESSING(Set.of("OUT_FOR_DELIVERY", "CANCELLED")),
    OUT_FOR_DELIVERY(Set.of("DELIVERED")),
    DELIVERED(Set.of()),
    CANCELLED(Set.of());

    private final Set<String> allowedNextStatuses;

    OrderStatus(Set<String> allowedNextStatuses) {
        this.allowedNextStatuses = allowedNextStatuses;
    }

    public boolean canTransitionTo(String targetStatus) {
        return allowedNextStatuses.contains(targetStatus);
    }
}
