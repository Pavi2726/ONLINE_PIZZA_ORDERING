package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.OrderItem;
import com.pizza.entity.Pizza;
import com.pizza.repository.CustomerRepository;
import com.pizza.repository.OrderRepository;
import com.pizza.repository.PizzaRepository;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of admin order management (US-017, US-018): real
 * {@code MockMvc} calls through the real {@link com.pizza.controller.AdminOrderController},
 * real {@link com.pizza.service.AdminOrderService} and the real
 * {@link OrderRepository} backed by H2, exercising the full {@link
 * com.pizza.entity.OrderStatus} transition graph.
 */
class AdminOrderManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PizzaRepository pizzaRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    private Order seedOrder(String status) {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Pizza pizza = pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Margherita", new BigDecimal("10.00"), "Classic", true));
        Order order = TestDataFactory.order(customer, LocalDateTime.now(), status);
        OrderItem item = TestDataFactory.orderItem(order, pizza, 1);
        order.addOrderItem(item);
        return orderRepository.saveAndFlush(order);
    }

    private void postStatusUpdate(Long orderId, String targetStatus, String expectedFlashKey, String expectedFlashValue)
            throws Exception {
        mockMvc.perform(post("/admin/orders/" + orderId + "/status")
                        .param("targetStatus", targetStatus)
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute(expectedFlashKey, expectedFlashValue));
    }

    // ------------------------------------------------------- full valid chain

    @Test
    void fullValidStatusChain_placedToProcessingToOutForDeliveryToDelivered_persistsEachRealTransition() throws Exception {
        Order order = seedOrder("PLACED");
        Long id = order.getId();
        String orderNumber = order.getOrderNumber();

        postStatusUpdate(id, "PROCESSING", "successMessage",
                "Order \"" + orderNumber + "\" is now PROCESSING.");
        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("PROCESSING");

        postStatusUpdate(id, "OUT_FOR_DELIVERY", "successMessage",
                "Order \"" + orderNumber + "\" is now OUT_FOR_DELIVERY.");
        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("OUT_FOR_DELIVERY");

        postStatusUpdate(id, "DELIVERED", "successMessage",
                "Order \"" + orderNumber + "\" is now DELIVERED.");
        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("DELIVERED");
    }

    // --------------------------------------------------------- invalid skip

    @Test
    void skipAheadTransition_fromPlacedToOutForDelivery_isRejectedWithFlashError_andRealStatusUnchanged() throws Exception {
        Order order = seedOrder("PLACED");
        Long id = order.getId();

        postStatusUpdate(id, "OUT_FOR_DELIVERY", "errorMessage",
                "Cannot move an order from PLACED to OUT_FOR_DELIVERY.");

        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("PLACED");
    }

    // ----------------------------------------------------- terminal state

    @Test
    void transitionFromTerminalDeliveredState_isRejectedWithFlashError_andRealStatusUnchanged() throws Exception {
        Order order = seedOrder("DELIVERED");
        Long id = order.getId();

        postStatusUpdate(id, "PROCESSING", "errorMessage",
                "Cannot move an order from DELIVERED to PROCESSING.");

        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void transitionFromTerminalCancelledState_isRejectedWithFlashError_andRealStatusUnchanged() throws Exception {
        Order order = seedOrder("CANCELLED");
        Long id = order.getId();

        postStatusUpdate(id, "PROCESSING", "errorMessage",
                "Cannot move an order from CANCELLED to PROCESSING.");

        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    // ------------------------------------------------- search/filter/sort (Task 9)

    /**
     * Real orders with different statuses, real {@link OrderRepository} query
     * methods (fetch-joined, same shape as {@code findAllOrdered()}), real
     * template render: {@code GET /admin/orders?status=X} must show only
     * orders in that status - proving the new repository queries don't hit a
     * lazy-loading error (the template reads {@code order.customer.fullName}
     * and {@code order.orderItems.size()}) and that the filter actually narrows
     * the result set end-to-end.
     */
    @Test
    void listOrders_filteredByStatus_rendersOnlyMatchingStatusOrders() throws Exception {
        Order placed = seedOrder("PLACED");
        Order delivered = seedOrder("DELIVERED");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/admin/orders").param("status", "DELIVERED").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(delivered.getOrderNumber())))
                .andExpect(content().string(not(containsString(placed.getOrderNumber()))));
    }

    // ------------------------------------------------------- bulk status update (Task 10)

    /**
     * Real orders in a mixed batch: two {@code PLACED} orders (eligible for
     * {@code PROCESSING}) and one {@code DELIVERED} order (terminal, not
     * eligible). Confirms the partial-success UX end-to-end through the real
     * {@link OrderRepository}: eligible orders actually change status in the
     * database, the ineligible one is left untouched, and the redirect/flash
     * reflects the mixed outcome.
     */
    @Test
    void bulkUpdateStatus_mixedEligibility_updatesOnlyEligibleOrders_inRealDatabase() throws Exception {
        Order eligible1 = seedOrder("PLACED");
        Order eligible2 = seedOrder("PLACED");
        Order ineligible = seedOrder("DELIVERED");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/admin/orders/bulk-status")
                        .param("orderIds", String.valueOf(eligible1.getId()),
                                String.valueOf(eligible2.getId()), String.valueOf(ineligible.getId()))
                        .param("targetStatus", "PROCESSING")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attribute("warningMessage",
                        "Changed status of 2 order(s) successfully, couldn't change status of 1 order(s) (invalid transition)."));

        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(eligible1.getId()).orElseThrow().getStatus())
                .isEqualTo("PROCESSING");
        assertThat(orderRepository.findByIdWithDetails(eligible2.getId()).orElseThrow().getStatus())
                .isEqualTo("PROCESSING");
        assertThat(orderRepository.findByIdWithDetails(ineligible.getId()).orElseThrow().getStatus())
                .isEqualTo("DELIVERED");
    }
}
