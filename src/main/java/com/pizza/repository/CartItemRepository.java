package com.pizza.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pizza.entity.Cart;
import com.pizza.entity.CartItem;
import com.pizza.entity.Pizza;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndPizza(Cart cart, Pizza pizza);

    Optional<CartItem> findByIdAndCart_Username(Long id, String username);

    @Query("SELECT COALESCE(SUM(ci.quantity),0) FROM CartItem ci WHERE ci.cart.username = :username")
    Integer sumQuantityByCartUsername(@Param("username") String username);

}