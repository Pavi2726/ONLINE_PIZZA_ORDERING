package com.pizza.controller;

import com.pizza.entity.Pizza;
import com.pizza.service.PizzaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the public home page with a hero section and featured pizzas.
 */
@Controller
@RequiredArgsConstructor
public class CustomerController {

    private static final int FEATURED_LIMIT = 4;

    private final PizzaService pizzaService;

    @GetMapping("/")
    public String home(Model model) {
        List<Pizza> featured = pizzaService.findAll().stream()
                .filter(Pizza::isAvailable)
                .limit(FEATURED_LIMIT)
                .toList();
        model.addAttribute("featuredPizzas", featured);
        return "home";
    }
}
