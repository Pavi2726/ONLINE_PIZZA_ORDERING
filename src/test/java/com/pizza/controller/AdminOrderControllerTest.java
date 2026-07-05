package com.pizza.controller;

import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
import com.pizza.service.AdminOrderService;
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
 * {@code @WebMvcTest} slice for {@link AdminOrderController}. Every route
 * lives under {@code /admin/orders/**}, which {@code WebMvcConfig} protects
 * with {@code AdminAuthInterceptor} (no exclusions for this sub-path), so an
 * admin session is required everywhere.
 *
 * <p>Reading {@link AdminOrderController#updateStatus} shows the call to
 * {@code adminOrderService.updateStatus(...)} is wrapped in a try/catch
 * inside the controller itself: on failure it flashes {@code errorMessage}
 * and redirects back to the order detail page, exactly like the success
 * path - it never lets the exception reach {@code GlobalExceptionHandler}.
 * The failed-transition test below asserts that actual behavior (redirect +
 * error flash), not a 500.
 */
@WebMvcTest(AdminOrderController.class)
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminOrderService adminOrderService;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    static Stream<Arguments> everyRoute() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/admin/orders"),
                Arguments.of(HttpMethod.GET, "/admin/orders/1"),
                Arguments.of(HttpMethod.POST, "/admin/orders/1/status"));
    }

    @ParameterizedTest(name = "{0} {1} with no admin session redirects to /admin/login")
    @MethodSource("everyRoute")
    void everyRoute_withNoAdminSession_redirectsToAdminLogin(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verifyNoInteractions(adminOrderService);
    }

    // --------------------------------------------------------------------- list

    @Test
    void list_withAdminSession_rendersOrderList() throws Exception {
        Customer customer = TestDataFactory.customer();
        Order order = TestDataFactory.order(customer);
        order.setId(1L);
        order.setCreatedAt(LocalDateTime.now());
        Pizza pizza = TestDataFactory.pizza();
        order.addOrderItem(TestDataFactory.orderItem(order, pizza, 2));

        when(adminOrderService.findAll()).thenReturn(List.of(order));

        mockMvc.perform(request(HttpMethod.GET, "/admin/orders").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-order-list"))
                .andExpect(model().attribute("orders", List.of(order)));
    }

    // ------------------------------------------------------------------- detail

    @Test
    void detail_withAdminSession_rendersOrderDetail() throws Exception {
        Customer customer = TestDataFactory.customer();
        Order order = TestDataFactory.order(customer);
        order.setId(1L);
        order.setCreatedAt(LocalDateTime.now());
        Pizza pizza = TestDataFactory.pizza();
        order.addOrderItem(TestDataFactory.orderItem(order, pizza, 1));

        when(adminOrderService.getById(1L)).thenReturn(order);

        mockMvc.perform(request(HttpMethod.GET, "/admin/orders/1").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-order-detail"))
                .andExpect(model().attribute("order", order));
    }

    // --------------------------------------------------------- status update

    @Test
    void updateStatus_withValidTransition_redirectsToDetailWithSuccessFlash() throws Exception {
        Customer customer = TestDataFactory.customer();
        Order updated = TestDataFactory.order(customer, LocalDateTime.now(), "PROCESSING");
        updated.setId(1L);
        when(adminOrderService.updateStatus(1L, "PROCESSING")).thenReturn(updated);

        mockMvc.perform(request(HttpMethod.POST, "/admin/orders/1/status")
                        .param("targetStatus", "PROCESSING")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/1"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(adminOrderService).updateStatus(1L, "PROCESSING");
    }

    /**
     * Confirms the actual, in-controller-caught failure behavior: an invalid
     * transition does NOT propagate to {@code GlobalExceptionHandler} (no
     * 500) - the controller's own try/catch flashes the error message and
     * redirects back to the same detail page, same as the happy path.
     */
    @Test
    void updateStatus_withInvalidTransition_isCaughtInController_redirectsWithErrorFlash_notA500() throws Exception {
        when(adminOrderService.updateStatus(1L, "DELIVERED"))
                .thenThrow(new IllegalStateException("Cannot move an order from PLACED to DELIVERED."));

        mockMvc.perform(request(HttpMethod.POST, "/admin/orders/1/status")
                        .param("targetStatus", "DELIVERED")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/1"))
                .andExpect(flash().attribute("errorMessage", "Cannot move an order from PLACED to DELIVERED."));

        verify(adminOrderService).updateStatus(1L, "DELIVERED");
    }
}
