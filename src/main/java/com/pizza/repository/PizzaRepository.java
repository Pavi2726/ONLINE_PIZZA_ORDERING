package com.pizza.repository;

import com.pizza.entity.Pizza;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for {@link Pizza} catalogue items. */
@Repository
public interface PizzaRepository extends JpaRepository<Pizza, Long> {

    List<Pizza> findByCategory(String category);

    List<Pizza> findByNameContainingIgnoreCase(String name);

    List<Pizza> findByCategoryAndNameContainingIgnoreCase(String category, String name);

    List<Pizza> findByAvailableTrue();
}
