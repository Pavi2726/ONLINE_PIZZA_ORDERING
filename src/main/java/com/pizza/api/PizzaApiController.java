package com.pizza.api;

import com.pizza.api.dto.ApiResponses.PizzaListResponse;
import com.pizza.service.PizzaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only catalogue. The customer menu and the home page's featured strip
 * both read from here; the "add to an existing order" mode of the menu is purely a UI
 * state (an {@code ?orderId=} in the SPA's URL) and needs no separate endpoint.
 */
@RestController
@RequestMapping("/api/pizzas")
@RequiredArgsConstructor
public class PizzaApiController {

    private final PizzaService pizzaService;

    @GetMapping
    public PizzaListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort) {
        return new PizzaListResponse(
                ApiMappers.pizzas(pizzaService.search(search, category, sort)),
                pizzaService.findCategories());
    }
}
