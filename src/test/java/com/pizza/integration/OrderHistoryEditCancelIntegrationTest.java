package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Cart;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.OrderItem;
import com.pizza.entity.Pizza;
import com.pizza.repository.CustomerRepository;
import com.pizza.repository.OrderRepository;
import com.pizza.repository.PizzaRepository;
import com.pizza.service.CartService;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of order history, the 5-minute edit window and cancellation
 * (US-007): real {@code MockMvc} calls through the real {@code OrderApiController}, real
 * {@link com.pizza.service.OrderService} and the real {@link OrderRepository} (its custom
 * fetch-join queries included) against H2.
 *
 * <p>Timestamp-dependent behaviour (the 5-minute edit window) is exercised by seeding
 * {@code Order.orderTime} directly via repository save with an explicit past
 * {@link LocalDateTime} - never by sleeping in the test.
 *
 * <p>Two expectations deliberately changed with the API: violating the edit window or
 * cancelling twice used to fall through to the generic handler as an uncaught HTTP 500.
 * The API maps {@code IllegalStateException} to a 409 Conflict with the real message, so
 * the client can show the user why instead of a blank error page.
 */
class OrderHistoryEditCancelIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PizzaRepository pizzaRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private EntityManager entityManager;

    private Customer persistedCustomer() {
        return customerRepository.saveAndFlush(TestDataFactory.customer());
    }

    private Pizza persistedPizza(BigDecimal price) {
        return pizzaRepository.saveAndFlush(TestDataFactory.pizza("Margherita", price, "Classic", true));
    }

    private Order seedOrder(Customer customer, LocalDateTime orderTime, String status, Pizza pizza, int quantity) {
        Order order = TestDataFactory.order(customer, orderTime, status);
        OrderItem item = TestDataFactory.orderItem(order, pizza, quantity);
        order.addOrderItem(item);
        return orderRepository.saveAndFlush(order);
    }

    private MockHttpSession customerSession(Customer customer) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_CUSTOMER, customer);
        return session;
    }

    // -------------------------------------------------------------- history

    @Test
    void history_returnsTheCustomersOwnOrdersWithComputedStatusMetadata() throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        seedOrder(customer, LocalDateTime.now(), "PLACED", pizza, 1);
        MockHttpSession session = customerSession(customer);

        // stepIndex / estimatedWindow / cancellable are computed server-side from
        // OrderStatus, so the browser never restates the progression rules.
        mockMvc.perform(get("/api/orders").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PLACED"))
                .andExpect(jsonPath("$[0].stepIndex").value(0))
                .andExpect(jsonPath("$[0].estimatedWindow").value("45–60 min"))
                .andExpect(jsonPath("$[0].cancellable").value(true));
    }

    // -------------------------------------------------------- within window

    @Test
    void editingOrderWithinFiveMinuteWindow_increaseQuantityAndUpdateDetails_persistsRealChangesInH2()
            throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        Order seeded = seedOrder(customer, LocalDateTime.now().minusSeconds(30), "PLACED", pizza, 1);
        Long orderId = seeded.getId();
        Long itemId = seeded.getOrderItems().get(0).getId();
        MockHttpSession session = customerSession(customer);

        mockMvc.perform(post("/api/orders/{orderId}/items/{itemId}/increase", orderId, itemId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pizza quantity updated successfully."));

        Order afterIncrease = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        assertThat(afterIncrease.getOrderItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(afterIncrease.getOrderItems().get(0).getLineTotal()).isEqualByComparingTo("20.00");
        // Totals are recalculated end-to-end from the real order items: no
        // coupon, so subtotal 20.00, 8% tax = 1.60, total = 21.60.
        assertThat(afterIncrease.getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(afterIncrease.getTax()).isEqualByComparingTo("1.60");
        assertThat(afterIncrease.getTotalAmount()).isEqualByComparingTo("21.60");

        mockMvc.perform(put("/api/orders/{orderId}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "deliveryAddress", "42 New Address Lane",
                                "phone", "9998887777")))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Order updated successfully."));

        Order afterAddressUpdate = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        assertThat(afterAddressUpdate.getDeliveryAddress()).isEqualTo("42 New Address Lane");
        assertThat(afterAddressUpdate.getPhone()).isEqualTo("9998887777");
    }

    // ------------------------------------------------------- outside window

    @Test
    void loadEditPage_outsideFiveMinuteWindow_isConflictWithFriendlyMessage() throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        Order expired = seedOrder(customer, LocalDateTime.now().minusMinutes(10), "PLACED", pizza, 1);
        MockHttpSession session = customerSession(customer);

        mockMvc.perform(get("/api/orders/{orderId}", expired.getId()).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "The edit time for this order has expired. Please place a new order to make changes."));
    }

    @Test
    void mutation_outsideFiveMinuteWindow_isConflictAndLeavesOrderUnchanged() throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        Order expired = seedOrder(customer, LocalDateTime.now().minusMinutes(10), "PLACED", pizza, 1);
        Long orderId = expired.getId();
        String originalAddress = expired.getDeliveryAddress();
        String originalPhone = expired.getPhone();
        MockHttpSession session = customerSession(customer);

        // OrderService.updateOrderDetails calls validateEditWindow() BEFORE mutating the
        // order, so nothing is written. The resulting IllegalStateException is now mapped
        // to a 409 rather than surfacing as an uncaught 500.
        mockMvc.perform(put("/api/orders/{orderId}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "deliveryAddress", "Should not be applied",
                                "phone", "1112223333")))
                        .session(session))
                .andExpect(status().isConflict());

        Order unchanged = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        assertThat(unchanged.getDeliveryAddress()).isEqualTo(originalAddress);
        assertThat(unchanged.getPhone()).isEqualTo(originalPhone);
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void cancelOrder_fromPlaced_succeeds_thenCancellingAgainFails() throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        Order placed = seedOrder(customer, LocalDateTime.now(), "PLACED", pizza, 1);
        Long orderId = placed.getId();
        MockHttpSession session = customerSession(customer);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Order cancelled successfully."));

        Order afterFirstCancel = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        assertThat(afterFirstCancel.getStatus()).isEqualTo("CANCELLED");

        // Cancelling an already-cancelled order throws IllegalStateException
        // ("Only placed orders can be cancelled."), now mapped to a 409.
        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId).session(session))
                .andExpect(status().isConflict());

        Order afterSecondAttempt = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        assertThat(afterSecondAttempt.getStatus()).isEqualTo("CANCELLED");
    }

    // ---------------------------------------------------------------- reorder (Task 7)

    @Test
    void reorder_appendsPastOrderItemsOntoWhateverIsAlreadyInTheCart() throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        // A DELIVERED order well outside the 5-minute edit window - reorder
        // must work for orders in any status, unlike edit/cancel.
        Order delivered = seedOrder(customer, LocalDateTime.now().minusDays(3), "DELIVERED", pizza, 2);
        Long orderId = delivered.getId();
        MockHttpSession session = customerSession(customer);

        // Cart already has one unit of the same pizza before reordering, to
        // prove reorder APPENDS rather than clearing/replacing cart contents.
        cartService.addPizzaToCart(customer.getEmail(), pizza.getId());

        mockMvc.perform(post("/api/orders/{orderId}/reorder", orderId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("1 item(s) added to your cart."));

        // Force Hibernate to synchronize with H2 and drop cached state, so the
        // read below reflects genuine database rows rather than the stale,
        // already-initialized in-memory Cart.cartItems collection from the
        // earlier addPizzaToCart call (same technique as
        // CartOwnershipIdorIntegrationTest).
        entityManager.flush();
        entityManager.clear();

        Cart cart = cartService.getCart(customer.getEmail());
        assertThat(cart.getCartItems()).hasSize(1);
        // 1 (pre-existing) + 2 (reordered quantity) = 3.
        assertThat(cart.getCartItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    void reorder_withUnavailablePizza_skipsItAndLeavesCartUntouchedForThatLine() throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        Order delivered = seedOrder(customer, LocalDateTime.now().minusDays(1), "DELIVERED", pizza, 2);
        Long orderId = delivered.getId();
        MockHttpSession session = customerSession(customer);

        // The pizza has since been marked unavailable by an admin.
        pizza.setAvailable(false);
        pizzaRepository.saveAndFlush(pizza);

        mockMvc.perform(post("/api/orders/{orderId}/reorder", orderId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "0 item(s) added to your cart. 1 unavailable, skipped."));

        entityManager.flush();
        entityManager.clear();

        Cart cart = cartService.getCart(customer.getEmail());
        assertThat(cart.getCartItems()).isEmpty();
    }
}
