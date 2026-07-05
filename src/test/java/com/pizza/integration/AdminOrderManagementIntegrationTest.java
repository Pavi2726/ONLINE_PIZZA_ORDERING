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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
