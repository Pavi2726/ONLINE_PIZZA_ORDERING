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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of admin order management (US-018): the status machine, its
 * rejections, filtering, and the bulk update - all against real H2 rows.
 *
 * <p>An invalid transition raises {@code IllegalStateException}, now mapped to a 409
 * with the real message rather than a flash redirect. A bulk update where some orders
 * are ineligible still answers 200, but with {@code messageType: "warning"} - the same
 * partial-success semantics the flash-based flow had.
 */
class AdminOrderManagementIntegrationTest extends AbstractIntegrationTest {

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

    private void updateStatusExpectingSuccess(Long orderId, String targetStatus, String orderNumber)
            throws Exception {
        mockMvc.perform(post("/api/admin/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetStatus", targetStatus)))
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Order \"" + orderNumber + "\" is now " + targetStatus + "."));
    }

    private void updateStatusExpectingRejection(Long orderId, String targetStatus, String expectedMessage)
            throws Exception {
        mockMvc.perform(post("/api/admin/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetStatus", targetStatus)))
                        .session(adminSession()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    @Test
    void fullValidStatusChain_placedToProcessingToOutForDeliveryToDelivered_persistsEachRealTransition()
            throws Exception {
        Order order = seedOrder("PLACED");
        Long id = order.getId();
        String orderNumber = order.getOrderNumber();

        updateStatusExpectingSuccess(id, "PROCESSING", orderNumber);
        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("PROCESSING");

        updateStatusExpectingSuccess(id, "OUT_FOR_DELIVERY", orderNumber);
        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus())
                .isEqualTo("OUT_FOR_DELIVERY");

        updateStatusExpectingSuccess(id, "DELIVERED", orderNumber);
        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void skipAheadTransition_fromPlacedToOutForDelivery_isRejected_andRealStatusUnchanged() throws Exception {
        Order order = seedOrder("PLACED");
        Long id = order.getId();

        updateStatusExpectingRejection(id, "OUT_FOR_DELIVERY",
                "Cannot move an order from PLACED to OUT_FOR_DELIVERY.");

        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("PLACED");
    }

    @Test
    void transitionFromTerminalDeliveredState_isRejected_andRealStatusUnchanged() throws Exception {
        Order order = seedOrder("DELIVERED");
        Long id = order.getId();

        updateStatusExpectingRejection(id, "PROCESSING",
                "Cannot move an order from DELIVERED to PROCESSING.");

        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void transitionFromTerminalCancelledState_isRejected_andRealStatusUnchanged() throws Exception {
        Order order = seedOrder("CANCELLED");
        Long id = order.getId();

        updateStatusExpectingRejection(id, "PROCESSING",
                "Cannot move an order from CANCELLED to PROCESSING.");

        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(id).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    /**
     * The detail response advertises exactly the transitions the status machine permits,
     * so the admin UI renders its buttons without restating the rules.
     */
    @Test
    void orderDetail_advertisesOnlyTheTransitionsTheStatusMachineAllows() throws Exception {
        Order placed = seedOrder("PLACED");
        mockMvc.perform(get("/api/admin/orders/{id}", placed.getId()).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedNextStatuses.length()").value(2))
                .andExpect(jsonPath("$.allowedNextStatuses").value(
                        org.hamcrest.Matchers.containsInAnyOrder("PROCESSING", "CANCELLED")));

        Order delivered = seedOrder("DELIVERED");
        mockMvc.perform(get("/api/admin/orders/{id}", delivered.getId()).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedNextStatuses.length()").value(0));
    }

    /** Admin order views render the customer, so the association must be serialized safely. */
    @Test
    void orderDetail_includesTheCustomerButNeverTheirPasswordHash() throws Exception {
        Order order = seedOrder("PLACED");

        String body = mockMvc.perform(get("/api/admin/orders/{id}", order.getId()).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.fullName").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("password");
    }

    @Test
    void listOrders_filteredByStatus_returnsOnlyMatchingStatusOrders() throws Exception {
        Order placed = seedOrder("PLACED");
        Order delivered = seedOrder("DELIVERED");
        entityManager.flush();
        entityManager.clear();

        String body = mockMvc.perform(get("/api/admin/orders")
                        .param("status", "DELIVERED")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains(delivered.getOrderNumber());
        assertThat(body).doesNotContain(placed.getOrderNumber());
    }

    @Test
    void bulkUpdateStatus_mixedEligibility_updatesOnlyEligibleOrders_inRealDatabase() throws Exception {
        Order eligible1 = seedOrder("PLACED");
        Order eligible2 = seedOrder("PLACED");
        Order ineligible = seedOrder("DELIVERED");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/admin/orders/bulk-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "orderIds", List.of(eligible1.getId(), eligible2.getId(), ineligible.getId()),
                                "targetStatus", "PROCESSING")))
                        .session(adminSession()))
                .andExpect(status().isOk())
                // Partial success is a warning, not a success - as it was with the flash.
                .andExpect(jsonPath("$.messageType").value("warning"))
                .andExpect(jsonPath("$.message").value(
                        "Changed status of 2 order(s) successfully, "
                                + "couldn't change status of 1 order(s) (invalid transition)."));

        entityManager.flush();
        entityManager.clear();
        assertThat(orderRepository.findByIdWithDetails(eligible1.getId()).orElseThrow().getStatus())
                .isEqualTo("PROCESSING");
        assertThat(orderRepository.findByIdWithDetails(eligible2.getId()).orElseThrow().getStatus())
                .isEqualTo("PROCESSING");
        assertThat(orderRepository.findByIdWithDetails(ineligible.getId()).orElseThrow().getStatus())
                .isEqualTo("DELIVERED");
    }

    @Test
    void bulkUpdateStatus_whereAllOrdersAreEligible_reportsSuccessNotWarning() throws Exception {
        Order eligible1 = seedOrder("PLACED");
        Order eligible2 = seedOrder("PLACED");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/admin/orders/bulk-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "orderIds", List.of(eligible1.getId(), eligible2.getId()),
                                "targetStatus", "PROCESSING")))
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageType").value("success"));
    }
}
