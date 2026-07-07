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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack proof that the Cloudinary-delete-ordering bug (Bug #3) is fixed,
 * against a REAL H2 foreign-key constraint. {@code PizzaServiceTest}
 * (unit-level) already locks in the fixed call order - {@code
 * pizzaRepository.delete()} + {@code flush()} before {@code
 * cloudinaryService.delete()} - with a mocked repository. This class proves
 * the real-world consequence against a genuine {@code order_items} row
 * referencing the pizza in H2: the DB delete is rejected by the FK
 * constraint, the Cloudinary image is never touched, and the pizza row
 * survives completely intact.
 *
 * <p><b>Why {@code Propagation.NOT_SUPPORTED}:</b> verified empirically -
 * the same reasoning as the original (pre-fix) version of this test still
 * applies. Left under {@link AbstractIntegrationTest}'s inherited per-test
 * rollback transaction, {@code PizzaService.delete()}'s own {@code
 * @Transactional} merely joins the already-open test transaction, and once
 * {@code pizzaRepository.flush()} fails, Hibernate's persistence context
 * keeps treating the pizza as removed (it was already marked for removal
 * before the rejected flush) for the rest of that shared persistence
 * context - so a subsequent {@code pizzaRepository.findById} on the SAME
 * joined transaction reports the pizza gone even though the real H2 row was
 * never actually deleted (the DELETE statement itself was rejected by the FK
 * constraint). Opting this test method out of that wrapping transaction lets
 * {@code PizzaService.delete()}'s {@code @Transactional} be the genuine,
 * request-scoped transaction boundary - matching real production traffic -
 * so the subsequent read-back in this test goes through a fresh persistence
 * context and reflects genuine H2 state.
 *
 * <p>Because this test does not run inside a rolled-back transaction, it
 * cleans up everything it writes in a {@code finally} block.
 */
class PizzaDeletionConstraintIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void delete_pizzaReferencedByRealOrderItem_redirectsWithFriendlyFlashError_andLeavesCloudinaryImageAndPizzaRowIntact() throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long pizzaId = pizza.getId();
        String imagePublicId = pizza.getImagePublicId();

        Order order = TestDataFactory.order(customer);
        order.addOrderItem(TestDataFactory.orderItem(order, pizza, 2));
        Order savedOrder = orderRepository.saveAndFlush(order);
        Long orderId = savedOrder.getId();

        try {
            // Real /admin/pizzas/delete/{id} endpoint, real admin session,
            // real PizzaService, real H2 foreign-key constraint on
            // order_items.pizza_id -> pizzas.id.
            mockMvc.perform(post("/admin/pizzas/delete/" + pizzaId).session(adminSession()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/pizzas"))
                    .andExpect(flash().attribute("errorMessage",
                            "This pizza cannot be deleted because it has already been ordered."));

            // The Cloudinary image is never touched, since the DB delete
            // (and its forced flush) failed before PizzaService.delete()
            // ever reaches the cloudinaryService.delete(...) call.
            verify(cloudinaryService, never()).delete(imagePublicId);

            // Force a fresh read from H2, bypassing any stale persistence-
            // context state left over from the failed flush inside the
            // request just performed.
            entityManager.clear();
            Pizza survived = pizzaRepository.findById(pizzaId).orElseThrow(
                    () -> new AssertionError("Expected the pizza row to survive the rejected delete"));
            assertThat(survived.getImagePublicId()).isEqualTo(imagePublicId);
        } finally {
            // Manual cleanup: this test opted out of the per-test rollback,
            // so every row it committed must be removed explicitly. Deleting
            // the order first cascades (CascadeType.ALL + orphanRemoval) to
            // its order_items row, which is what was blocking the pizza delete.
            orderRepository.deleteById(orderId);
            pizzaRepository.deleteById(pizzaId);
            customerRepository.deleteById(customer.getId());
        }
    }
}
