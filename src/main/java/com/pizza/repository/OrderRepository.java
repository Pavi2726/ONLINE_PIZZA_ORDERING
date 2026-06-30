package com.pizza.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pizza.entity.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.orderItems oi
            LEFT JOIN FETCH oi.pizza
            JOIN FETCH o.customer
            WHERE o.orderNumber = :orderNumber
            AND o.customer.id = :customerId
            """)
    Optional<Order> findByOrderNumberAndCustomerId(
            @Param("orderNumber") String orderNumber,
            @Param("customerId") Long customerId);

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.orderItems oi
            LEFT JOIN FETCH oi.pizza
            WHERE o.customer.id = :customerId
            ORDER BY o.createdAt DESC
            """)
    List<Order> findAllByCustomerId(
            @Param("customerId") Long customerId);

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.orderItems oi
            LEFT JOIN FETCH oi.pizza
            JOIN FETCH o.customer
            WHERE o.id = :orderId
            AND o.customer.id = :customerId
            """)
    Optional<Order> findByIdAndCustomerId(
            @Param("orderId") Long orderId,
            @Param("customerId") Long customerId);

    boolean existsByOrderNumber(String orderNumber);

}