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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of order history, the 5-minute edit window and
 * cancellation (US-007): real {@code MockMvc} calls through the real
 * {@link com.pizza.controller.OrderController}, real
 * {@link com.pizza.service.OrderService} and the real {@link OrderRepository}
 * (its custom fetch-join queries included) against H2.
 *
 * <p>Timestamp-dependent behaviour (the 5-minute edit window) is exercised by
 * seeding {@code Order.orderTime} directly via repository save with an
 * explicit past {@link LocalDateTime} - never by sleeping in the test.
 */
class OrderHistoryEditCancelIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    // -------------------------------------------------------- within window

    @Test
    void editingOrderWithinFiveMinuteWindow_increaseQuantityAndUpdateDetails_persistsRealChangesInH2() throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        Order seeded = seedOrder(customer, LocalDateTime.now().minusSeconds(30), "PLACED", pizza, 1);
        Long orderId = seeded.getId();
        Long itemId = seeded.getOrderItems().get(0).getId();
        MockHttpSession session = customerSession(customer);

        mockMvc.perform(post("/orders/edit/" + orderId + "/increase/" + itemId).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/edit/" + orderId))
                .andExpect(flash().attribute("successMessage", "Pizza quantity updated successfully."));

        Order afterIncrease = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        assertThat(afterIncrease.getOrderItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(afterIncrease.getOrderItems().get(0).getLineTotal()).isEqualByComparingTo("20.00");
        // Totals are recalculated end-to-end from the real order items: no
        // coupon, so subtotal 20.00, 8% tax = 1.60, total = 21.60.
        assertThat(afterIncrease.getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(afterIncrease.getTax()).isEqualByComparingTo("1.60");
        assertThat(afterIncrease.getTotalAmount()).isEqualByComparingTo("21.60");

        mockMvc.perform(post("/orders/edit/" + orderId)
                        .param("deliveryAddress", "42 New Address Lane")
                        .param("phone", "9998887777")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/history"))
                .andExpect(flash().attribute("successMessage", "Order updated successfully."));

        Order afterAddressUpdate = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        assertThat(afterAddressUpdate.getDeliveryAddress()).isEqualTo("42 New Address Lane");
        assertThat(afterAddressUpdate.getPhone()).isEqualTo("9998887777");
    }

    // ------------------------------------------------------- outside window

    @Test
    void showEditPage_outsideFiveMinuteWindow_redirectsToHistoryWithFriendlyFlash() throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        Order expired = seedOrder(customer, LocalDateTime.now().minusMinutes(10), "PLACED", pizza, 1);
        MockHttpSession session = customerSession(customer);

        mockMvc.perform(get("/orders/edit/" + expired.getId()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/history"))
                .andExpect(flash().attribute("errorMessage",
                        "The edit time for this order has expired. Please place a new order to make changes."));
    }

    @Test
    void postMutation_outsideFiveMinuteWindow_surfacesAsUncaught500AndLeavesOrderUnchanged() throws Exception {
        Customer customer = persistedCustomer();
        Pizza pizza = persistedPizza(new BigDecimal("10.00"));
        Order expired = seedOrder(customer, LocalDateTime.now().minusMinutes(10), "PLACED", pizza, 1);
        Long orderId = expired.getId();
        String originalAddress = expired.getDeliveryAddress();
        String originalPhone = expired.getPhone();
        MockHttpSession session = customerSession(customer);

        // OrderService.updateOrderDetails calls validateEditWindow() BEFORE
        // mutating the order; that throws an IllegalStateException which has
        // no dedicated @ExceptionHandler in GlobalExceptionHandler, so it
        // falls through to the generic Exception handler -> HTTP 500 (not a
        // friendly redirect - only the GET edit page does that).
        mockMvc.perform(post("/orders/edit/" + orderId)
                        .param("deliveryAddress", "Should not be applied")
                        .param("phone", "1112223333")
                        .session(session))
                .andExpect(status().isInternalServerError());

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

        mockMvc.perform(post("/orders/cancel/" + orderId).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/history"))
                .andExpect(flash().attribute("successMessage", "Order cancelled successfully."));

        Order afterFirstCancel = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        assertThat(afterFirstCancel.getStatus()).isEqualTo("CANCELLED");

        // Cancelling an already-cancelled order throws IllegalStateException
        // ("Only placed orders can be cancelled."), which - like the expired
        // edit-window case above - is not specifically handled and surfaces
        // as an uncaught 500 via the generic Exception handler.
        mockMvc.perform(post("/orders/cancel/" + orderId).session(session))
                .andExpect(status().isInternalServerError());

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

        mockMvc.perform(post("/orders/reorder/" + orderId).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("successMessage", "1 item(s) added to your cart."));

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

        mockMvc.perform(post("/orders/reorder/" + orderId).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("successMessage",
                        "0 item(s) added to your cart. 1 unavailable, skipped."));

        entityManager.flush();
        entityManager.clear();

        Cart cart = cartService.getCart(customer.getEmail());
        assertThat(cart.getCartItems()).isEmpty();
    }
}
