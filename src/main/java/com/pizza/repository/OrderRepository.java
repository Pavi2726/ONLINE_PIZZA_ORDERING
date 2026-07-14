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
            LEFT JOIN FETCH oi.drink
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
            LEFT JOIN FETCH oi.drink
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
            LEFT JOIN FETCH oi.drink
            JOIN FETCH o.customer
            WHERE o.id = :orderId
            AND o.customer.id = :customerId
            """)
    Optional<Order> findByIdAndCustomerId(
            @Param("orderId") Long orderId,
            @Param("customerId") Long customerId);

    boolean existsByOrderNumber(String orderNumber);

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.orderItems oi
            LEFT JOIN FETCH oi.pizza
            LEFT JOIN FETCH oi.drink
            JOIN FETCH o.customer
            ORDER BY o.createdAt DESC
            """)
    List<Order> findAllOrdered();

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.orderItems oi
            LEFT JOIN FETCH oi.pizza
            LEFT JOIN FETCH oi.drink
            JOIN FETCH o.customer
            WHERE o.id = :orderId
            """)
    Optional<Order> findByIdWithDetails(@Param("orderId") Long orderId);

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.orderItems oi
            LEFT JOIN FETCH oi.pizza
            LEFT JOIN FETCH oi.drink
            JOIN FETCH o.customer
            WHERE LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(o.customer.firstName) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(o.customer.lastName) LIKE LOWER(CONCAT('%', :term, '%'))
            ORDER BY o.createdAt DESC
            """)
    List<Order> searchByOrderNumberOrCustomerName(@Param("term") String term);

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.orderItems oi
            LEFT JOIN FETCH oi.pizza
            LEFT JOIN FETCH oi.drink
            JOIN FETCH o.customer
            WHERE o.status = :status
            ORDER BY o.createdAt DESC
            """)
    List<Order> findByStatus(@Param("status") String status);

    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.orderItems oi
            LEFT JOIN FETCH oi.pizza
            LEFT JOIN FETCH oi.drink
            JOIN FETCH o.customer
            WHERE o.status = :status
              AND (LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(o.customer.firstName) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(o.customer.lastName) LIKE LOWER(CONCAT('%', :term, '%')))
            ORDER BY o.createdAt DESC
            """)
    List<Order> searchByTermAndStatus(@Param("term") String term, @Param("status") String status);

}