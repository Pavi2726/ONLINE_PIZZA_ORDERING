package com.pizza.controller;

import com.pizza.entity.Pizza;
import com.pizza.service.CartService;
import com.pizza.service.PizzaService;
import com.pizza.testsupport.TestDataFactory;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link PizzaController}. {@code /pizzas} is
 * customer-facing public browsing (US-003): it is deliberately reachable with
 * no session at all, since neither {@code AdminAuthInterceptor} (guards
 * {@code /admin/**}) nor {@code CustomerAuthInterceptor} (guards
 * {@code /orders/**}) is registered against this path in
 * {@code WebMvcConfig}.
 */
@WebMvcTest(PizzaController.class)
class PizzaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PizzaService pizzaService;

    // GlobalModelAdvice (loaded in every @WebMvcTest slice) now depends on
    // CartService for the navbar cart-badge model attribute; /pizzas is
    // reachable with no session at all, so it's never actually invoked, but
    // the bean must still exist for the ApplicationContext to start.
    @MockBean
    private CartService cartService;

    @Test
    void list_isReachableWithNoSessionAtAll() throws Exception {
        when(pizzaService.search(null, null, null)).thenReturn(List.of());
        when(pizzaService.findCategories()).thenReturn(List.of());

        // No .session(...) is attached to this request at all: confirms public
        // browsing works without any prior login, customer or admin.
        mockMvc.perform(get("/pizzas"))
                .andExpect(status().isOk())
                .andExpect(view().name("pizza-list"));
    }

    @Test
    void list_passesSearchCategoryAndSortThroughToService_andPopulatesModel() throws Exception {
        Pizza match = TestDataFactory.pizza("Veg Margherita", new BigDecimal("9.99"), "Veg", true);
        when(pizzaService.search("marg", "Veg", "priceAsc")).thenReturn(List.of(match));
        when(pizzaService.findCategories()).thenReturn(List.of("Classic", "Veg"));

        mockMvc.perform(get("/pizzas")
                        .param("search", "marg")
                        .param("category", "Veg")
                        .param("sort", "priceAsc"))
                .andExpect(status().isOk())
                .andExpect(view().name("pizza-list"))
                .andExpect(model().attribute("pizzas", List.of(match)))
                .andExpect(model().attribute("categories", List.of("Classic", "Veg")))
                .andExpect(model().attribute("search", "marg"))
                .andExpect(model().attribute("selectedCategory", "Veg"))
                .andExpect(model().attribute("sort", "priceAsc"));

        verify(pizzaService).search("marg", "Veg", "priceAsc");
    }

    @Test
    void list_withNoQueryParams_searchesWithAllNulls() throws Exception {
        when(pizzaService.search(null, null, null)).thenReturn(List.of());
        when(pizzaService.findCategories()).thenReturn(List.of());

        mockMvc.perform(get("/pizzas"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("search", (Object) null))
                .andExpect(model().attribute("selectedCategory", (Object) null))
                .andExpect(model().attribute("sort", (Object) null))
                .andExpect(model().attribute("orderId", (Object) null));

        verify(pizzaService).search(null, null, null);
    }

    @Test
    void list_withOrderId_passesItThroughAsModelAttribute() throws Exception {
        when(pizzaService.search(null, null, null)).thenReturn(List.of());
        when(pizzaService.findCategories()).thenReturn(List.of());

        mockMvc.perform(get("/pizzas").param("orderId", "42"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("orderId", 42L));
    }
}
