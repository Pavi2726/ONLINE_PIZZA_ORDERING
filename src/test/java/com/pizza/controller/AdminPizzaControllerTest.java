package com.pizza.controller;

import com.pizza.dto.PizzaDTO;
import com.pizza.entity.Pizza;
import com.pizza.service.CartService;
import com.pizza.service.PizzaService;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link AdminPizzaController}. Every route here
 * lives under {@code /admin/pizzas/**}, which {@code WebMvcConfig} protects
 * with {@code AdminAuthInterceptor} (no exclusions for this sub-path), so an
 * admin session is required everywhere; this test asserts that boundary for
 * every route, then exercises the add-pizza validation and happy paths with a
 * mock admin session in place.
 */
@WebMvcTest(AdminPizzaController.class)
class AdminPizzaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PizzaService pizzaService;

    // GlobalModelAdvice (loaded in every @WebMvcTest slice) now depends on
    // CartService for the navbar cart-badge model attribute; admin pages have
    // no logged-in customer so it's never actually invoked, but the bean must
    // still exist for the ApplicationContext to start.
    @MockBean
    private CartService cartService;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    static Stream<Arguments> everyRoute() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/admin/pizzas"),
                Arguments.of(HttpMethod.GET, "/admin/pizzas/add"),
                Arguments.of(HttpMethod.POST, "/admin/pizzas/add"),
                Arguments.of(HttpMethod.GET, "/admin/pizzas/edit/1"),
                Arguments.of(HttpMethod.POST, "/admin/pizzas/edit/1"),
                Arguments.of(HttpMethod.POST, "/admin/pizzas/delete/1"));
    }

    @ParameterizedTest(name = "{0} {1} with no admin session redirects to /admin/login")
    @MethodSource("everyRoute")
    void everyRoute_withNoAdminSession_redirectsToAdminLogin(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verifyNoInteractions(pizzaService);
    }

    // --------------------------------------------------------------------- list

    @Test
    void list_withAdminSession_rendersManagementList() throws Exception {
        Pizza pizza = TestDataFactory.pizza();
        when(pizzaService.search(null, null, null)).thenReturn(List.of(pizza));
        when(pizzaService.findCategories()).thenReturn(List.of("Classic"));

        mockMvc.perform(request(HttpMethod.GET, "/admin/pizzas").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-pizza-list"))
                .andExpect(model().attribute("pizzas", List.of(pizza)))
                .andExpect(model().attribute("categories", List.of("Classic")));
    }

    // ---------------------------------------------------------------- add form

    @Test
    void showAddForm_withAdminSession_rendersFormWithEmptyCommandObject() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/admin/pizzas/add").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("add-pizza"))
                .andExpect(model().attributeExists("pizzaDTO"));
    }

    @Test
    void add_withMissingImage_reRendersFormWithValidationError_notA500() throws Exception {
        MockMultipartFile emptyImage = new MockMultipartFile("image", "", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/admin/pizzas/add")
                        .file(emptyImage)
                        .param("name", "Margherita")
                        .param("description", "Classic tomato and cheese")
                        .param("category", "Classic")
                        .param("price", "9.99")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("add-pizza"))
                .andExpect(model().attributeHasFieldErrors("pizzaDTO", "imageUrl"));

        verify(pizzaService, never()).add(any(), any());
    }

    @Test
    void add_withValidDataAndImage_redirectsToListWithSuccessFlash() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "pizza.png", "image/png", new byte[]{1, 2, 3});
        Pizza saved = TestDataFactory.pizza("Margherita", new BigDecimal("9.99"), "Classic", true);
        when(pizzaService.add(any(PizzaDTO.class), any())).thenReturn(saved);

        mockMvc.perform(multipart("/admin/pizzas/add")
                        .file(image)
                        .param("name", "Margherita")
                        .param("description", "Classic tomato and cheese")
                        .param("category", "Classic")
                        .param("price", "9.99")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/pizzas"))
                .andExpect(flash().attribute("successMessage", "Pizza \"Margherita\" added successfully."));
    }

    // --------------------------------------------------------------- edit form

    @Test
    void showEditForm_withAdminSession_populatesDtoFromExistingPizza() throws Exception {
        Pizza pizza = TestDataFactory.pizza();
        pizza.setId(1L);
        PizzaDTO dto = new PizzaDTO();
        dto.setId(1L);
        dto.setName(pizza.getName());
        when(pizzaService.findById(1L)).thenReturn(pizza);
        when(pizzaService.toDto(pizza)).thenReturn(dto);

        mockMvc.perform(request(HttpMethod.GET, "/admin/pizzas/edit/1").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("edit-pizza"))
                .andExpect(model().attribute("pizzaDTO", dto))
                .andExpect(model().attribute("pizzaId", 1L));
    }

    // ------------------------------------------------------------------ delete

    @Test
    void delete_withAdminSession_redirectsToListAndCallsService() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/admin/pizzas/delete/1").session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/pizzas"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(pizzaService).delete(1L);
    }
}
