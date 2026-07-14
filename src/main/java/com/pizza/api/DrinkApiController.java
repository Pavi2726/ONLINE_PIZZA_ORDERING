package com.pizza.api;

import com.pizza.api.dto.ApiResponses.DrinkListResponse;
import com.pizza.api.dto.ApiResponses.DrinkResponse;
import com.pizza.entity.Drink;
import com.pizza.service.DrinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only drink catalogue API — accessible to all (no auth required).
 * Cart mutations are in {@link CartApiController}.
 */
@RestController
@RequestMapping("/api/drinks")
@RequiredArgsConstructor
public class DrinkApiController {

    private final DrinkService drinkService;

    /** All available categories from the current catalogue. */
    @GetMapping("/categories")
    public List<String> categories() {
        return DrinkService.PREDEFINED_CATEGORIES;
    }

    /**
     * Lists drinks with optional search, category filter, and sort.
     * Only returns available drinks to customers.
     */
    @GetMapping
    public DrinkListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort) {

        List<Drink> drinks = drinkService.search(search, category, sort)
                .stream()
                .filter(Drink::isAvailable)
                .toList();

        return new DrinkListResponse(
                ApiMappers.drinks(drinks),
                DrinkService.PREDEFINED_CATEGORIES);
    }

    /** Single drink by ID. */
    @GetMapping("/{id}")
    public DrinkResponse get(@PathVariable Long id) {
        return ApiMappers.drink(drinkService.findById(id));
    }
}
