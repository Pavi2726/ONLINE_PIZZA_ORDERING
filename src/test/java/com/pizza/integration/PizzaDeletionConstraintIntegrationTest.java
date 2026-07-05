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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Full-stack characterization of the Cloudinary-delete-ordering bug against a
 * REAL H2 foreign-key constraint (task-7-brief.md's "Correction from Task
 * 6"). {@code PizzaServiceTest} (Task 2, unit-level) already locked in the
 * call ORDER of {@link com.pizza.service.PizzaService#delete(Long)} -
 * {@code cloudinaryService.delete()} before {@code pizzaRepository.delete()}
 * - with a fully mocked repository that never actually fails. This class
 * proves the real-world consequence of that ordering against a genuine
 * {@code order_items} row referencing the pizza in H2: the Cloudinary image
 * is unrecoverably gone (the mock was invoked - in production this would be
 * a real, non-reversible network call), but the pizza row survives because
 * the database rejects the delete and the whole operation rolls back.
 *
 * <p><b>Why {@code Propagation.NOT_SUPPORTED}:</b> verified empirically
 * against this exact scenario (mirroring {@code
 * AdminCouponManagementIntegrationTest}'s duplicate-code characterization
 * test, which hit the identical issue). Left under {@link
 * AbstractIntegrationTest}'s inherited per-test rollback transaction, {@code
 * PizzaService.delete()}'s own {@code @Transactional} merely joins the
 * already-open test transaction instead of owning/committing one, so
 * Hibernate never flushes the pending {@code DELETE} statement to H2 during
 * the request - {@code AdminPizzaController.delete()} sees no exception at
 * all and happily redirects with a false-positive "deleted successfully"
 * flash, and a subsequent {@code pizzaRepository.findById} then reports the
 * pizza gone purely because JPA hides not-yet-flushed removed entities from
 * their own persistence context - not because any row was ever removed from
 * H2. Opting this test method out of that wrapping transaction lets {@code
 * PizzaService.delete()}'s {@code @Transactional} be the genuine,
 * request-scoped transaction boundary - matching real production traffic -
 * so its commit-time flush (and the resulting real {@code
 * DataIntegrityViolationException}) happens synchronously inside {@code
 * pizzaService.delete(id)}, exactly as it would for a real admin request.
 * That exception is uncaught by {@code AdminPizzaController.delete()} (no
 * try/catch there), so it reaches {@code GlobalExceptionHandler}'s generic
 * {@code DataAccessException} handler, which renders the friendly {@code
 * error} view at HTTP 500 - confirmed from source, not assumed.
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

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void delete_pizzaReferencedByRealOrderItem_failsAsDatabaseErrorPage_butCloudinaryImageIsAlreadyGone() throws Exception {
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
                    .andExpect(status().isInternalServerError())
                    .andExpect(view().name("error"))
                    .andExpect(model().attribute("message",
                            "A database error occurred. Please try again later."));

            // The image is already gone from Cloudinary - PizzaService.delete()
            // calls cloudinaryService.delete() BEFORE the repository delete
            // that then failed. In production this Cloudinary call is a real,
            // irreversible network operation.
            verify(cloudinaryService).delete(imagePublicId);

            // ...yet the pizza row survives in H2: the failed delete's own
            // transaction rolled back, so the row is exactly as it was before
            // the request. This is the bug: an orphaned catalogue row now
            // pointing at an image that no longer exists anywhere.
            Pizza survived = pizzaRepository.findById(pizzaId).orElseThrow(
                    () -> new AssertionError("Expected the pizza row to survive the failed, rolled-back delete"));
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
