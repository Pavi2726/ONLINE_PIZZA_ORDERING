package com.pizza.controller;

import com.pizza.entity.Cart;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
import com.pizza.service.CartService;
import com.pizza.service.OrderService;
import com.pizza.service.PizzaService;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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
 * <p><b>Dead-route characterization test:</b> {@code showOrderForm_} below is
 * the key test in this class. Reading the source confirms the whole chain:
 * {@code OrderController.showOrderForm} renders {@code place-order.html},
 * whose form does {@code th:field="*{pizzaId}"} and {@code th:field="*{quantity}"}
 * against the {@code orderDTO} model attribute - but {@link
 * com.pizza.dto.OrderDTO} only has {@code deliveryAddress}, {@code phone} and
 * {@code couponCode}; it has no {@code pizzaId}/{@code quantity} properties.
 * {@code @WebMvcTest} auto-configures the real Thymeleaf {@code ViewResolver}
 * (it is not mocked), so rendering this view for a real request throws
 * {@link org.springframework.beans.NotReadablePropertyException} while
 * evaluating {@code *{pizzaId}}.
 *
 * <p><b>This does NOT surface as a handled 500 via {@code
 * GlobalExceptionHandler}</b> - that was verified empirically, not assumed.
 * {@code DispatcherServlet.doDispatch} only routes exceptions thrown by
 * <i>handler invocation</i> (the try/catch around {@code ha.handle(...)})
 * through {@code processHandlerException}, which is what lets {@code
 * @ExceptionHandler} methods in {@code GlobalExceptionHandler} run. View
 * rendering (Thymeleaf's {@code TemplateEngine.process}) happens later, in
 * {@code DispatcherServlet.render}, called from {@code processDispatchResult}
 * - a step that sits <i>outside</i> that try/catch. A rendering exception
 * therefore propagates straight up out of {@code doDispatch}/{@code
 * doService}/{@code processRequest} and out of {@code MockMvc.perform(...)}
 * itself; {@code GlobalExceptionHandler.handleGeneric} is never invoked, and
 * no {@code MvcResult}/HTTP status is ever produced for this slice test to
 * assert on. (Confirmed by running this exact test and inspecting the
 * resulting stack trace: {@code NotReadablePropertyException} bubbles from
 * {@code ThymeleafView.render} through {@code DispatcherServlet.render}
 * straight into the test method - no {@code ExceptionHandlerExceptionResolver}
 * frame appears anywhere in it.) This route is unreachable without an
 * uncaught exception today; the test exists to document that fact precisely,
 * not to assert the "correct" (200 view) or the "plausible" (clean 500
 * response) outcome.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private PizzaService pizzaService;

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
                Arguments.of(HttpMethod.GET, "/orders/new?pizzaId=1"),
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

        verifyNoInteractions(orderService, pizzaService, cartService);
    }

    // --------------------------------------------------- /orders/new (dead route)

    @Test
    void showOrderForm_withValidSessionAndAvailablePizza_throwsDuringViewRendering_notCaughtByGlobalExceptionHandler()
            throws Exception {
        Customer customer = TestDataFactory.customer();
        Pizza pizza = TestDataFactory.pizza();
        pizza.setId(1L);
        when(pizzaService.findById(1L)).thenReturn(pizza);

        // No .andExpect(status()...) here: the exception happens during view
        // rendering (see class Javadoc), which is outside the phase that
        // produces an HTTP response at all in this @WebMvcTest slice - the
        // exception propagates directly out of perform() itself.
        Exception thrown = Assertions.assertThrows(Exception.class, () ->
                mockMvc.perform(request(HttpMethod.GET, "/orders/new")
                        .param("pizzaId", "1")
                        .session(customerSession(customer))));

        Throwable root = thrown;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(NotReadablePropertyException.class);
        assertThat(root.getMessage()).contains("pizzaId").contains("OrderDTO");
    }

    @Test
    void showOrderForm_withUnavailablePizza_redirectsToPizzasWithoutRenderingTemplate() throws Exception {
        Customer customer = TestDataFactory.customer();
        Pizza pizza = TestDataFactory.pizza("Out of stock pizza", new BigDecimal("9.99"), "Classic", false);
        pizza.setId(2L);
        when(pizzaService.findById(2L)).thenReturn(pizza);

        mockMvc.perform(request(HttpMethod.GET, "/orders/new")
                        .param("pizzaId", "2")
                        .session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pizzas"))
                .andExpect(flash().attribute("errorMessage", "This pizza is currently unavailable."));
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
