package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
import com.pizza.repository.CustomerRepository;
import com.pizza.repository.OrderRepository;
import com.pizza.repository.PizzaRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A pizza that has already been ordered must not be deletable, and the rejected delete
 * must not take its Cloudinary image with it.
 *
 * <p>The IllegalStateException the service raises is now mapped to a 409 Conflict
 * carrying the real message, where the server-rendered app redirected with a flash.
 */
class PizzaDeletionConstraintIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void delete_pizzaReferencedByRealOrderItem_isConflict_andLeavesCloudinaryImageAndPizzaRowIntact()
            throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long pizzaId = pizza.getId();
        String imagePublicId = pizza.getImagePublicId();

        Order order = TestDataFactory.order(customer);
        order.addOrderItem(TestDataFactory.orderItem(order, pizza, 2));
        Order savedOrder = orderRepository.saveAndFlush(order);
        Long orderId = savedOrder.getId();

        try {
            mockMvc.perform(delete("/api/admin/pizzas/{id}", pizzaId).session(adminSession()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "This pizza cannot be deleted because it has already been ordered."));

            verify(cloudinaryService, never()).delete(imagePublicId);

            entityManager.clear();
            Pizza survived = pizzaRepository.findById(pizzaId).orElseThrow(
                    () -> new AssertionError("Expected the pizza row to survive the rejected delete"));
            assertThat(survived.getImagePublicId()).isEqualTo(imagePublicId);
        } finally {
            orderRepository.deleteById(orderId);
            pizzaRepository.deleteById(pizzaId);
            customerRepository.deleteById(customer.getId());
        }
    }
}
