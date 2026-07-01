package com.pizza.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pizza.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

}