package com.pizza.service;

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
}
