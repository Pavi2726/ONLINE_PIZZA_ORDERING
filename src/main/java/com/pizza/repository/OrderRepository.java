package com.pizza.repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pizza.entity.Order;

/** Data access for {@link Order} records. */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT o FROM Order o "
            + "JOIN FETCH o.customer "
            + "JOIN FETCH o.pizza "
            + "WHERE o.orderNumber = :orderNumber AND o.customer.id = :customerId")
    Optional<Order> findByOrderNumberAndCustomerId(
            @Param("orderNumber") String orderNumber,
            @Param("customerId") Long customerId);
           
            @Query("""
       SELECT o
       FROM Order o
       JOIN FETCH o.pizza
       WHERE o.customer.id = :customerId
       ORDER BY o.createdAt DESC
       """)
List<Order> findAllByCustomerId(@Param("customerId") Long customerId);

@Query("""
       SELECT o
       FROM Order o
       JOIN FETCH o.pizza
       JOIN FETCH o.customer
       WHERE o.id = :orderId
       AND o.customer.id = :customerId
       """)
Optional<Order> findByIdAndCustomerId(
        @Param("orderId") Long orderId,
        @Param("customerId") Long customerId);

    boolean existsByOrderNumber(String orderNumber);
}
