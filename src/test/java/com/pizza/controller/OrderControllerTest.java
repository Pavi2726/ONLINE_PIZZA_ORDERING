package com.pizza.controller;

import com.pizza.entity.Cart;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
import com.pizza.service.CartService;
import com.pizza.service.OrderService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link OrderController}. {@code WebMvcConfig}
 * registers {@code CustomerAuthInterceptor} against every {@code /orders/**}
 * path with no exclusions, so a customer session is required everywhere; that
 * interceptor is a {@code @Component} {@code HandlerInterceptor} and loads
 * automatically in this slice.
 *
 * <p>Bug #1/#7 fix: the dead {@code GET /orders/new}/{@code POST /orders}
 * legacy single-pizza order form has been removed entirely (along with
 * {@code place-order.html} and the unused {@code EditOrderDTO}/{@code
 * EditOrderItemDTO}/{@code OrderService.getEditOrder()}), so {@code
 * OrderController} no longer depends on {@code PizzaService} at all.</p>
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CartService cartService;

    private MockHttpSession customerSession(Customer customer) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_CUSTOMER, customer);
        return session;
    }

    // -------------------------------------------------------- auth boundary

    static Stream<Arguments> everyRoute() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/orders/history"),
                Arguments.of(HttpMethod.GET, "/orders/checkout"),
                Arguments.of(HttpMethod.POST, "/orders/place"),
                Arguments.of(HttpMethod.POST, "/orders/cancel/1"),
                Arguments.of(HttpMethod.GET, "/orders/success/ORD-TEST-1"),
                Arguments.of(HttpMethod.GET, "/orders/edit/1"),
                Arguments.of(HttpMethod.POST, "/orders/edit/1"),
                Arguments.of(HttpMethod.POST, "/orders/edit/1/increase/1"),
                Arguments.of(HttpMethod.POST, "/orders/edit/1/decrease/1"),
                Arguments.of(HttpMethod.POST, "/orders/edit/1/remove/1"),
                Arguments.of(HttpMethod.POST, "/orders/edit/1/add-pizza?pizzaId=1"));
    }

    @ParameterizedTest(name = "{0} {1} with no customer session redirects to /login")
    @MethodSource("everyRoute")
    void everyOrdersRoute_withNoCustomerSession_redirectsToLogin(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(orderService, cartService);
    }

    // ------------------------------------------------------------- history

    @Test
    void viewOrderHistory_withCustomerSession_rendersHistory() throws Exception {
        Customer customer = TestDataFactory.customer();
        customer.setId(1L);
        Order order = TestDataFactory.order(customer);
        order.setId(1L);
        order.setCreatedAt(LocalDateTime.now());
        Pizza pizza = TestDataFactory.pizza();
        order.addOrderItem(TestDataFactory.orderItem(order, pizza, 2));

        when(orderService.getOrderHistory(1L)).thenReturn(List.of(order));

        mockMvc.perform(request(HttpMethod.GET, "/orders/history").session(customerSession(customer)))
                .andExpect(status().isOk())
                .andExpect(view().name("order-history"));
    }

    @Test
    void viewOrderHistory_withPlacedOrder_cancelFormCarriesConfirmOnsubmit() throws Exception {
        Customer customer = TestDataFactory.customer();
        customer.setId(1L);
        Order order = TestDataFactory.order(customer, LocalDateTime.now(), "PLACED");
        order.setId(1L);
        order.setCreatedAt(LocalDateTime.now());
        Pizza pizza = TestDataFactory.pizza();
        order.addOrderItem(TestDataFactory.orderItem(order, pizza, 1));

        when(orderService.getOrderHistory(1L)).thenReturn(List.of(order));

        // Consistency with the admin-pizza-list delete pattern: the confirm()
        // guard belongs on the <form>'s onsubmit, not the <button>'s onclick.
        mockMvc.perform(request(HttpMethod.GET, "/orders/history").session(customerSession(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "onsubmit=\"return confirm('Are you sure you want to cancel this order?');\"")));
    }

    // ------------------------------------------------------------ checkout

    @Test
    void checkout_withNonEmptyCart_rendersCheckoutView() throws Exception {
        Customer customer = TestDataFactory.customer();
        Cart cart = TestDataFactory.cart(customer.getEmail());
        Pizza pizza = TestDataFactory.pizza();
        cart.getCartItems().add(TestDataFactory.cartItem(cart, pizza, 1));

        when(cartService.getCart(customer.getEmail())).thenReturn(cart);
        when(cartService.getCartSubtotal(customer.getEmail())).thenReturn(pizza.getPrice());

        mockMvc.perform(request(HttpMethod.GET, "/orders/checkout").session(customerSession(customer)))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"));
    }

    // --------------------------------------------------------------- place

    @Test
    void placeCartOrder_withCustomerSession_redirectsToSuccessPage() throws Exception {
        Customer customer = TestDataFactory.customer();
        Order savedOrder = TestDataFactory.order(customer);
        when(orderService.placeOrder(any(), any())).thenReturn(savedOrder);

        mockMvc.perform(request(HttpMethod.POST, "/orders/place").session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/success/" + savedOrder.getOrderNumber()))
                .andExpect(flash().attribute("successMessage", "Order placed successfully!"));

        verify(orderService).placeOrder(any(), any());
    }

    @Test
    void placeCartOrder_withStaleInvalidCoupon_redirectsToCheckoutWithFlashError() throws Exception {
        Customer customer = TestDataFactory.customer();
        when(orderService.placeOrder(any(), any()))
                .thenThrow(new IllegalArgumentException("Coupon is inactive."));

        mockMvc.perform(request(HttpMethod.POST, "/orders/place").session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/checkout"))
                .andExpect(flash().attribute("errorMessage", "Coupon is inactive."));
    }

    // -------------------------------------------------------------- cancel

    @Test
    void cancelOrder_withCustomerSession_redirectsToHistoryWithSuccessFlash() throws Exception {
        Customer customer = TestDataFactory.customer();
        customer.setId(5L);

        mockMvc.perform(request(HttpMethod.POST, "/orders/cancel/9").session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/history"))
                .andExpect(flash().attribute("successMessage", "Order cancelled successfully."));

        verify(orderService).cancelOrder(9L, 5L);
    }

    // ------------------------------------------------------- edit/** family

    @Test
    void showEditOrderPage_withCustomerSessionAndRecentOrder_rendersEditOrderView() throws Exception {
        Customer customer = TestDataFactory.customer();
        customer.setId(3L);
        Order order = TestDataFactory.order(customer, LocalDateTime.now(), "PLACED");
        order.setId(1L);
        order.setCreatedAt(LocalDateTime.now());
        Pizza pizza = TestDataFactory.pizza();
        order.addOrderItem(TestDataFactory.orderItem(order, pizza, 1));

        when(orderService.findOrderById(1L, 3L)).thenReturn(order);

        mockMvc.perform(request(HttpMethod.GET, "/orders/edit/1").session(customerSession(customer)))
                .andExpect(status().isOk())
                .andExpect(view().name("edit-order"));
    }

    @Test
    void increaseQuantity_withCustomerSession_redirectsBackToEditPage() throws Exception {
        Customer customer = TestDataFactory.customer();
        customer.setId(4L);

        mockMvc.perform(request(HttpMethod.POST, "/orders/edit/1/increase/2").session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/edit/1"))
                .andExpect(flash().attribute("successMessage", "Pizza quantity updated successfully."));

        verify(orderService).increaseItemQuantity(1L, 2L, 4L);
    }
}
