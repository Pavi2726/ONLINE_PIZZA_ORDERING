package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Cart;
import com.pizza.entity.CartItem;
import com.pizza.entity.Customer;
import com.pizza.entity.Drink;
import com.pizza.entity.Order;
import com.pizza.entity.OrderItem;
import com.pizza.entity.OrderStatus;
import com.pizza.repository.CartItemRepository;
import com.pizza.repository.CartRepository;
import com.pizza.repository.CustomerRepository;
import com.pizza.repository.DrinkRepository;
import com.pizza.repository.OrderItemRepository;
import com.pizza.repository.OrderRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * A drink that is still on an active order (not yet {@code DELIVERED}/{@code CANCELLED})
 * must not be deletable, and the rejected delete must not take its Cloudinary image with it.
 * Once every order referencing the drink is terminal, the delete must succeed: historical
 * order items keep their price/quantity/total but lose the live drink reference, and any
 * open shopping cart holding the drink is silently cleared. Mirrors
 * {@code PizzaDeletionConstraintIntegrationTest}.
 */
class DrinkDeletionConstraintIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DrinkRepository drinkRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private EntityManager entityManager;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void delete_drinkReferencedByActiveOrderItem_isConflict_andLeavesCloudinaryImageAndDrinkRowIntact()
            throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Drink drink = drinkRepository.saveAndFlush(TestDataFactory.drink());
        Long drinkId = drink.getId();
        String imagePublicId = drink.getImagePublicId();

        Order order = TestDataFactory.order(customer);
        order.addOrderItem(TestDataFactory.orderItem(order, drink, 2));
        Order savedOrder = orderRepository.saveAndFlush(order);
        Long orderId = savedOrder.getId();

        try {
            mockMvc.perform(delete("/api/admin/drinks/{id}", drinkId).session(adminSession()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "This drink cannot be deleted because it is part of an order "
                                    + "that has not been delivered or cancelled yet."));

            verify(cloudinaryService, never()).delete(imagePublicId);

            entityManager.clear();
            Drink survived = drinkRepository.findById(drinkId).orElseThrow(
                    () -> new AssertionError("Expected the drink row to survive the rejected delete"));
            assertThat(survived.getImagePublicId()).isEqualTo(imagePublicId);
        } finally {
            orderRepository.deleteById(orderId);
            drinkRepository.deleteById(drinkId);
            customerRepository.deleteById(customer.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void delete_drinkReferencedOnlyByDeliveredOrder_succeeds_andNullsOutTheHistoricalOrderItem()
            throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Drink drink = drinkRepository.saveAndFlush(TestDataFactory.drink());
        Long drinkId = drink.getId();
        String imagePublicId = drink.getImagePublicId();
        BigDecimal originalPrice = drink.getPrice();

        Order order = TestDataFactory.order(customer, LocalDateTime.now().minusDays(2),
                OrderStatus.DELIVERED.name());
        OrderItem orderItem = TestDataFactory.orderItem(order, drink, 2);
        order.addOrderItem(orderItem);
        Order savedOrder = orderRepository.saveAndFlush(order);
        Long orderId = savedOrder.getId();
        Long orderItemId = orderItem.getId();

        try {
            mockMvc.perform(delete("/api/admin/drinks/{id}", drinkId).session(adminSession()))
                    .andExpect(status().isOk());

            verify(cloudinaryService).delete(imagePublicId);

            entityManager.clear();
            assertThat(drinkRepository.findById(drinkId)).isEmpty();

            OrderItem reloadedItem = orderItemRepository.findById(orderItemId).orElseThrow();
            assertThat(reloadedItem.getDrink()).isNull();
            assertThat(reloadedItem.getQuantity()).isEqualTo(2);
            assertThat(reloadedItem.getPrice()).isEqualByComparingTo(originalPrice);
        } finally {
            orderRepository.deleteById(orderId);
            customerRepository.deleteById(customer.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void delete_drinkReferencedOnlyByCancelledOrder_succeeds() throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Drink drink = drinkRepository.saveAndFlush(TestDataFactory.drink());
        Long drinkId = drink.getId();

        Order order = TestDataFactory.order(customer, LocalDateTime.now().minusDays(2),
                OrderStatus.CANCELLED.name());
        order.addOrderItem(TestDataFactory.orderItem(order, drink, 1));
        Order savedOrder = orderRepository.saveAndFlush(order);
        Long orderId = savedOrder.getId();

        try {
            mockMvc.perform(delete("/api/admin/drinks/{id}", drinkId).session(adminSession()))
                    .andExpect(status().isOk());

            entityManager.clear();
            assertThat(drinkRepository.findById(drinkId)).isEmpty();
        } finally {
            orderRepository.deleteById(orderId);
            customerRepository.deleteById(customer.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void delete_drinkReferencedByOneCancelledAndOnePlacedOrder_isStillConflict() throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Drink drink = drinkRepository.saveAndFlush(TestDataFactory.drink());
        Long drinkId = drink.getId();

        Order cancelledOrder = TestDataFactory.order(customer, LocalDateTime.now().minusDays(2),
                OrderStatus.CANCELLED.name());
        cancelledOrder.addOrderItem(TestDataFactory.orderItem(cancelledOrder, drink, 1));
        Order savedCancelledOrder = orderRepository.saveAndFlush(cancelledOrder);

        Order placedOrder = TestDataFactory.order(customer, LocalDateTime.now(), OrderStatus.PLACED.name());
        placedOrder.addOrderItem(TestDataFactory.orderItem(placedOrder, drink, 1));
        Order savedPlacedOrder = orderRepository.saveAndFlush(placedOrder);

        try {
            mockMvc.perform(delete("/api/admin/drinks/{id}", drinkId).session(adminSession()))
                    .andExpect(status().isConflict());

            entityManager.clear();
            assertThat(drinkRepository.findById(drinkId)).isPresent();
        } finally {
            orderRepository.deleteById(savedCancelledOrder.getId());
            orderRepository.deleteById(savedPlacedOrder.getId());
            drinkRepository.deleteById(drinkId);
            customerRepository.deleteById(customer.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void delete_drinkInAnotherCustomersOpenCart_isSilentlyRemovedFromCart_deleteSucceeds() throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Drink drink = drinkRepository.saveAndFlush(TestDataFactory.drink());
        Long drinkId = drink.getId();

        Cart cart = TestDataFactory.cart(customer.getEmail());
        Cart savedCart = cartRepository.saveAndFlush(cart);
        CartItem cartItem = TestDataFactory.cartItem(savedCart, drink, 1);
        CartItem savedCartItem = cartItemRepository.saveAndFlush(cartItem);

        try {
            mockMvc.perform(delete("/api/admin/drinks/{id}", drinkId).session(adminSession()))
                    .andExpect(status().isOk());

            entityManager.clear();
            assertThat(drinkRepository.findById(drinkId)).isEmpty();
            assertThat(cartItemRepository.findById(savedCartItem.getId())).isEmpty();
        } finally {
            cartRepository.deleteById(savedCart.getId());
            customerRepository.deleteById(customer.getId());
        }
    }
}
