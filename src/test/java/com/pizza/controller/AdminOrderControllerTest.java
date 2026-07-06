package com.pizza.controller;

import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
import com.pizza.service.AdminOrderService;
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
                Arguments.of(HttpMethod.GET, "/admin/orders"),
                Arguments.of(HttpMethod.GET, "/admin/orders/1"),
                Arguments.of(HttpMethod.POST, "/admin/orders/1/status"),
                Arguments.of(HttpMethod.POST, "/admin/orders/bulk-status"));
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
    void list_withNoParams_isBackwardCompatible_dispatchesToSearchWithAllNulls() throws Exception {
        // Task 9: the controller now always calls adminOrderService.search(...);
        // with no query params it must call search(null, null, null), which the
        // service guarantees produces exactly findAll()'s output.
        Customer customer = TestDataFactory.customer();
        Order order = TestDataFactory.order(customer);
        order.setId(1L);
        order.setCreatedAt(LocalDateTime.now());
        Pizza pizza = TestDataFactory.pizza();
        order.addOrderItem(TestDataFactory.orderItem(order, pizza, 2));

        when(adminOrderService.search(null, null, null)).thenReturn(List.of(order));

        mockMvc.perform(request(HttpMethod.GET, "/admin/orders").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-order-list"))
                .andExpect(model().attribute("orders", List.of(order)))
                .andExpect(model().attribute("search", (Object) null))
                .andExpect(model().attribute("selectedStatus", (Object) null))
                .andExpect(model().attribute("sort", (Object) null));

        verify(adminOrderService).search(null, null, null);
    }

    @Test
    void list_withSearchStatusAndSortParams_bindsThemToServiceAndModel() throws Exception {
        Customer customer = TestDataFactory.customer();
        Order order = TestDataFactory.order(customer, LocalDateTime.now(), "PROCESSING");
        order.setId(2L);

        when(adminOrderService.search("jane", "PROCESSING", "oldest")).thenReturn(List.of(order));

        mockMvc.perform(request(HttpMethod.GET, "/admin/orders")
                        .param("search", "jane")
                        .param("status", "PROCESSING")
                        .param("sort", "oldest")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-order-list"))
                .andExpect(model().attribute("orders", List.of(order)))
                .andExpect(model().attribute("search", "jane"))
                .andExpect(model().attribute("selectedStatus", "PROCESSING"))
                .andExpect(model().attribute("sort", "oldest"));

        verify(adminOrderService).search("jane", "PROCESSING", "oldest");
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

    // --------------------------------------------------------- bulk status update

    @Test
    void bulkUpdateStatus_bindsOrderIdsListParam_andRedirectsToOrderList() throws Exception {
        when(adminOrderService.bulkUpdateStatus(List.of(1L, 2L, 3L), "PROCESSING"))
                .thenReturn(new AdminOrderService.BulkStatusUpdateResult(3, List.of()));

        mockMvc.perform(request(HttpMethod.POST, "/admin/orders/bulk-status")
                        .param("orderIds", "1", "2", "3")
                        .param("targetStatus", "PROCESSING")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"));

        verify(adminOrderService).bulkUpdateStatus(List.of(1L, 2L, 3L), "PROCESSING");
    }

    @Test
    void bulkUpdateStatus_allEligible_flashesSuccessMessage() throws Exception {
        when(adminOrderService.bulkUpdateStatus(List.of(1L, 2L), "PROCESSING"))
                .thenReturn(new AdminOrderService.BulkStatusUpdateResult(2, List.of()));

        mockMvc.perform(request(HttpMethod.POST, "/admin/orders/bulk-status")
                        .param("orderIds", "1", "2")
                        .param("targetStatus", "PROCESSING")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attribute("successMessage", "2 order(s) updated to PROCESSING."));
    }

    @Test
    void bulkUpdateStatus_mixedResult_flashesWarningMessage() throws Exception {
        when(adminOrderService.bulkUpdateStatus(List.of(1L, 2L), "PROCESSING"))
                .thenReturn(new AdminOrderService.BulkStatusUpdateResult(1, List.of("ORD-TEST-99")));

        mockMvc.perform(request(HttpMethod.POST, "/admin/orders/bulk-status")
                        .param("orderIds", "1", "2")
                        .param("targetStatus", "PROCESSING")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attribute("warningMessage",
                        "1 order(s) updated to PROCESSING; skipped (invalid transition): ORD-TEST-99."));
    }

    @Test
    void bulkUpdateStatus_serviceThrows_isCaughtInController_redirectsWithErrorFlash_notA500() throws Exception {
        when(adminOrderService.bulkUpdateStatus(List.of(1L), "NOT_A_STATUS"))
                .thenThrow(new IllegalArgumentException("Unknown target status: NOT_A_STATUS"));

        mockMvc.perform(request(HttpMethod.POST, "/admin/orders/bulk-status")
                        .param("orderIds", "1")
                        .param("targetStatus", "NOT_A_STATUS")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attribute("errorMessage", "Unknown target status: NOT_A_STATUS"));
    }
}
