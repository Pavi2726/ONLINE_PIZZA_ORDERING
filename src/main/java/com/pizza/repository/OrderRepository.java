package com.pizza.repository;

import com.pizza.entity.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    boolean existsByOrderNumber(String orderNumber);
}
