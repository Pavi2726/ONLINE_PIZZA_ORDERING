package com.pizza.controller;

import com.pizza.entity.Pizza;
import com.pizza.service.PizzaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Customer-facing pizza browsing (US-003). This controller is strictly
 * read-only: it lets customers view, search, filter and sort pizzas. All
 * create/update/delete operations live in {@link AdminPizzaController} under
 * {@code /admin/pizzas/**} and are never exposed here.
 */
@Controller
@RequestMapping("/pizzas")
@RequiredArgsConstructor
public class PizzaController {

    private final PizzaService pizzaService;

    /** US-003: list with search, category filter and price sort. */
    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            Model model) {
        List<Pizza> pizzas = pizzaService.search(search, category, sort);
        model.addAttribute("pizzas", pizzas);
        model.addAttribute("categories", pizzaService.findCategories());
        model.addAttribute("search", search);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("sort", sort);
        return "pizza-list";
    }
}
