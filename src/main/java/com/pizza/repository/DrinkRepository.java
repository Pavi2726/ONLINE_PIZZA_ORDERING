package com.pizza.repository;

import com.pizza.entity.Drink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data access for {@link Drink} catalogue items. */
@Repository
public interface DrinkRepository extends JpaRepository<Drink, Long> {

    List<Drink> findByCategory(String category);

    List<Drink> findByNameContainingIgnoreCase(String name);

    List<Drink> findByCategoryAndNameContainingIgnoreCase(String category, String name);

    List<Drink> findByAvailableTrue();
}
