package com.pizza.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pizza.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUsername(String username);

}