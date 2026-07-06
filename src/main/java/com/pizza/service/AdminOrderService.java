package com.pizza.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pizza.entity.Order;
import com.pizza.entity.OrderStatus;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/** Admin-facing order management (US-017, US-018). */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findAllOrdered();
    }

    /**
     * Order list query supporting search, status filter and total/date sort
     * (Task 9), mirroring {@link PizzaService#search}'s branch-in-Java /
     * in-memory-{@link Comparator} pattern.
     *
     * @param search optional order-number/customer-name fragment
     * @param status optional exact {@link OrderStatus} name ("" or null = all)
     * @param sort   "totalAsc", "totalDesc", "oldest" or null ("newest")
     */
    @Transactional(readOnly = true)
    public List<Order> search(String search, String status, String sort) {
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasStatus = status != null && !status.isBlank();

        List<Order> results;
        if (hasSearch && hasStatus) {
            results = orderRepository.searchByTermAndStatus(search.trim(), status);
        } else if (hasStatus) {
            results = orderRepository.findByStatus(status);
        } else if (hasSearch) {
            results = orderRepository.searchByOrderNumberOrCustomerName(search.trim());
        } else {
            results = orderRepository.findAllOrdered();
        }

        if ("totalAsc".equals(sort)) {
            results.sort(Comparator.comparing(Order::getTotalAmount));
        } else if ("totalDesc".equals(sort)) {
            results.sort(Comparator.comparing(Order::getTotalAmount).reversed());
        } else if ("oldest".equals(sort)) {
            results.sort(Comparator.comparing(Order::getCreatedAt));
        }
        // "newest" (or no sort) needs no re-sort: every branch above already returns createdAt DESC.
        return results;
    }

    @Transactional(readOnly = true)
    public Order getById(Long id) {
        return orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Transactional
    public Order updateStatus(Long id, String targetStatus) {
        Order order = getById(id);

        OrderStatus current;
        try {
            current = OrderStatus.valueOf(order.getStatus());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Order is in an unrecognized status: " + order.getStatus());
        }

        if (!isValidTarget(targetStatus)) {
            throw new IllegalArgumentException("Unknown target status: " + targetStatus);
        }

        if (!current.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    "Cannot move an order from " + current + " to " + targetStatus + ".");
        }

        order.setStatus(targetStatus);
        return orderRepository.save(order);
    }

    private boolean isValidTarget(String targetStatus) {
        try {
            OrderStatus.valueOf(targetStatus);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Result of a bulk status update (Task 10): how many orders were moved, and the order numbers skipped. */
    public record BulkStatusUpdateResult(int updatedCount, List<String> skippedOrderNumbers) {}

    /**
     * Applies {@code targetStatus} to every order in {@code orderIds} for
     * which the transition is valid, reusing {@link #isValidTarget(String)}
     * and {@link OrderStatus#canTransitionTo(String)} - the same
     * transition-validation rules {@link #updateStatus(Long, String)} uses
     * for a single order. Orders for which the transition isn't valid (or
     * whose current status isn't recognized) are skipped rather than
     * aborting the whole batch (confirmed partial-success UX).
     */
    @Transactional
    public BulkStatusUpdateResult bulkUpdateStatus(List<Long> orderIds, String targetStatus) {
        if (!isValidTarget(targetStatus)) {
            throw new IllegalArgumentException("Unknown target status: " + targetStatus);
        }

        int updated = 0;
        List<String> skipped = new ArrayList<>();

        for (Long id : orderIds) {
            Order order = getById(id);
            OrderStatus current;
            try {
                current = OrderStatus.valueOf(order.getStatus());
            } catch (IllegalArgumentException ex) {
                skipped.add(order.getOrderNumber());
                continue;
            }

            if (!current.canTransitionTo(targetStatus)) {
                skipped.add(order.getOrderNumber());
                continue;
            }

            order.setStatus(targetStatus);
            orderRepository.save(order);
            updated++;
        }

        return new BulkStatusUpdateResult(updated, skipped);
    }
}
