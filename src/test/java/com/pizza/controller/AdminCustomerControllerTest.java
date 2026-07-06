package com.pizza.controller;

import com.pizza.entity.Customer;
import com.pizza.service.AdminCustomerService;
import com.pizza.service.CartService;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link AdminCustomerController}. Every route
 * lives under {@code /admin/customers/**}, which {@code WebMvcConfig} protects
 * with {@code AdminAuthInterceptor} (no exclusions for this sub-path), so an
 * admin session is required everywhere.
 */
@WebMvcTest(AdminCustomerController.class)
class AdminCustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminCustomerService adminCustomerService;

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
                Arguments.of(HttpMethod.GET, "/admin/customers"),
                Arguments.of(HttpMethod.GET, "/admin/customers/edit/1"),
                Arguments.of(HttpMethod.POST, "/admin/customers/update/1"));
    }

    @ParameterizedTest(name = "{0} {1} with no admin session redirects to /admin/login")
    @MethodSource("everyRoute")
    void everyRoute_withNoAdminSession_redirectsToAdminLogin(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verifyNoInteractions(adminCustomerService);
    }

    // --------------------------------------------------------------------- list

    @Test
    void list_withAdminSession_rendersCustomerList() throws Exception {
        Customer customer = TestDataFactory.customer();
        customer.setId(1L);
        customer.setCreatedAt(LocalDateTime.now());
        // No query params -> controller must call search(null, null), which
        // AdminCustomerService.search reproduces findAll()'s exact output for.
        when(adminCustomerService.search(null, null)).thenReturn(List.of(customer));

        mockMvc.perform(request(HttpMethod.GET, "/admin/customers").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-customer-list"))
                .andExpect(model().attribute("customers", List.of(customer)))
                .andExpect(model().attribute("search", (Object) null))
                .andExpect(model().attribute("sort", (Object) null));
    }

    @Test
    void list_withSearchAndSortParams_bindsParamsThroughToServiceAndModel() throws Exception {
        Customer customer = TestDataFactory.customer();
        customer.setId(1L);
        when(adminCustomerService.search("jane", "nameAsc")).thenReturn(List.of(customer));

        mockMvc.perform(request(HttpMethod.GET, "/admin/customers")
                        .param("search", "jane")
                        .param("sort", "nameAsc")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-customer-list"))
                .andExpect(model().attribute("customers", List.of(customer)))
                .andExpect(model().attribute("search", "jane"))
                .andExpect(model().attribute("sort", "nameAsc"));

        verify(adminCustomerService).search("jane", "nameAsc");
    }

    // ---------------------------------------------------------------- edit form

    @Test
    void showEditForm_withAdminSession_populatesDtoFromExistingCustomer() throws Exception {
        Customer customer = TestDataFactory.customer();
        customer.setId(1L);
        when(adminCustomerService.getById(1L)).thenReturn(customer);

        mockMvc.perform(request(HttpMethod.GET, "/admin/customers/edit/1").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("edit-customer"))
                .andExpect(model().attribute("customerId", 1L))
                .andExpect(model().attributeExists("customerDTO"));
    }

    // ------------------------------------------------------------------ update

    @Test
    void updateCustomer_withValidData_redirectsToListWithSuccessFlash() throws Exception {
        Customer updated = TestDataFactory.customer();
        updated.setId(1L);
        when(adminCustomerService.updateCustomer(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(request(HttpMethod.POST, "/admin/customers/update/1")
                        .param("firstName", updated.getFirstName())
                        .param("lastName", updated.getLastName())
                        .param("email", updated.getEmail())
                        .param("phone", updated.getPhone())
                        .param("address", updated.getAddress())
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(adminCustomerService).updateCustomer(eq(1L), any());
    }

    @Test
    void updateCustomer_withBlankFirstName_reRendersFormWithValidationError_notA500() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/admin/customers/update/1")
                        .param("firstName", "")
                        .param("lastName", "Doe")
                        .param("email", "jane@example.test")
                        .param("phone", "5551234567")
                        .param("address", "1 Test Street")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("edit-customer"))
                .andExpect(model().attributeHasFieldErrors("customerDTO", "firstName"));

        verifyNoInteractions(adminCustomerService);
    }
}
