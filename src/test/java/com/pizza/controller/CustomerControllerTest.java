package com.pizza.controller;

import com.pizza.entity.Pizza;
import com.pizza.service.PizzaService;
import com.pizza.testsupport.TestDataFactory;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link CustomerController}: verifies the public
 * home page renders and that the featured-pizza selection filters out
 * unavailable pizzas and caps the list at {@code FEATURED_LIMIT} (4), matching
 * {@link CustomerController#home}.
 */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PizzaService pizzaService;

    @Test
    void home_rendersHomeView_withOnlyAvailablePizzasFeatured() throws Exception {
        Pizza available1 = TestDataFactory.pizza("Margherita", new BigDecimal("9.99"), "Classic", true);
        Pizza unavailable = TestDataFactory.pizza("Sold Out", new BigDecimal("12.99"), "Veg", false);
        Pizza available2 = TestDataFactory.pizza("Pepperoni", new BigDecimal("11.99"), "Meat", true);
        when(pizzaService.findAll()).thenReturn(List.of(available1, unavailable, available2));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("featuredPizzas", List.of(available1, available2)));
    }

    @Test
    void home_capsFeaturedPizzasAtFour_evenWhenMoreAreAvailable() throws Exception {
        List<Pizza> sixAvailable = List.of(
                TestDataFactory.pizza("P1", BigDecimal.ONE, "Classic", true),
                TestDataFactory.pizza("P2", BigDecimal.ONE, "Classic", true),
                TestDataFactory.pizza("P3", BigDecimal.ONE, "Classic", true),
                TestDataFactory.pizza("P4", BigDecimal.ONE, "Classic", true),
                TestDataFactory.pizza("P5", BigDecimal.ONE, "Classic", true),
                TestDataFactory.pizza("P6", BigDecimal.ONE, "Classic", true));
        when(pizzaService.findAll()).thenReturn(sixAvailable);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("featuredPizzas", sixAvailable.subList(0, 4)));
    }

    @Test
    void home_withNoAvailablePizzas_rendersEmptyFeaturedList() throws Exception {
        Pizza unavailable = TestDataFactory.pizza("Sold Out", new BigDecimal("12.99"), "Veg", false);
        when(pizzaService.findAll()).thenReturn(List.of(unavailable));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("featuredPizzas", List.of()));
    }
}
