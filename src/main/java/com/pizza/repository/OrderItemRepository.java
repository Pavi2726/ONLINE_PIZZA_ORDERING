package com.pizza.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pizza.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByPizza_IdAndOrder_StatusNotIn(Long pizzaId, Collection<String> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderItem oi SET oi.pizza = null WHERE oi.pizza.id = :pizzaId")
    int clearPizzaReferences(@Param("pizzaId") Long pizzaId);

    boolean existsByDrink_IdAndOrder_StatusNotIn(Long drinkId, Collection<String> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderItem oi SET oi.drink = null WHERE oi.drink.id = :drinkId")
    int clearDrinkReferences(@Param("drinkId") Long drinkId);

}