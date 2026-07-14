package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Cart;
import com.pizza.entity.CartItem;
import com.pizza.entity.Customer;
import com.pizza.entity.Pizza;
import com.pizza.repository.CartItemRepository;
import com.pizza.repository.CartRepository;
import com.pizza.repository.PizzaRepository;
import com.pizza.testsupport.TestDataFactory;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The definitive end-to-end proof that the cart-ownership IDOR (Bug #2) is still
 * closed after the move to the JSON API. {@code CartApiController}'s item routes
 * thread the caller's customer email down into {@link com.pizza.service.CartService},
 * which looks the cart item up scoped to that username via
 * {@code CartItemRepository.findByIdAndCart_Username} - so an id belonging to a
 * different customer's cart is rejected with a 404, and {@code /api/cart/**} is
 * covered by {@code CustomerAuthInterceptor} (see {@code WebMvcConfig}), so an
 * unauthenticated request never reaches the controller at all.
 *
 * <p>This class proves the fix against real H2-persisted data across two genuinely
 * separate, real customers (registered and logged in through the real API, each with
 * its own {@link MockHttpSession}): customer A adds a real pizza to their real cart,
 * and a request either authenticated as a completely different customer B, or carrying
 * no session at all, leaves A's real {@link CartItem} row completely untouched.
 *
 * <p>{@code entityManager.flush()}/{@code clear()} after each mutating call forces
 * Hibernate to synchronize with H2 and drop cached state, so every assertion below
 * reads back genuine database rows via {@link CartItemRepository}, not merely mutated
 * Java heap state from this test's shared persistence context.
 *
 * <p>The one deliberate change from the pre-SPA version: the unauthenticated case now
 * expects a 401, where it used to expect a 302 to {@code /login}. That is the whole
 * point of the API auth branch — a redirect is useless to an XHR.
 */
class CartOwnershipIdorIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PizzaRepository pizzaRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private EntityManager entityManager;

    /** A adds {@code pizza} to their real cart and returns the real, H2-persisted cartItemId. */
    private Long addToCartAndGetItemId(MockHttpSession victimSession, String victimEmail, Pizza pizza)
            throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pizzaId", pizza.getId())))
                        .session(victimSession))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        Cart cart = cartRepository.findByUsername(victimEmail).orElseThrow(
                () -> new AssertionError("Expected a real Cart row for " + victimEmail));
        assertThat(cart.getCartItems()).hasSize(1);
        return cart.getCartItems().get(0).getId();
    }

    // ================================================================
    // Attacker is a real, separately logged-in customer B: authenticated,
    // but not the owner of the targeted cart item -> rejected with a 404,
    // victim's row is untouched.
    // ================================================================

    @Test
    void increaseAndRemove_usingAnotherLoggedInCustomersSession_failsWithNotFound_leavesVictimsCartItemUntouched()
            throws Exception {
        Customer victimTemplate = TestDataFactory.customer();
        MockHttpSession victimSession = registerAndLogin(victimTemplate);

        Customer attackerTemplate = TestDataFactory.customer();
        MockHttpSession attackerSession = registerAndLogin(attackerTemplate);

        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long victimCartItemId = addToCartAndGetItemId(victimSession, victimTemplate.getEmail(), pizza);

        // Attacker B is authenticated as THEMSELVES, not as the victim, and targets the
        // victim's real cartItemId directly.
        mockMvc.perform(post("/api/cart/items/{cartItemId}/increase", victimCartItemId)
                        .session(attackerSession))
                .andExpect(status().isNotFound());

        entityManager.flush();
        entityManager.clear();
        CartItem afterIncrease = cartItemRepository.findById(victimCartItemId).orElseThrow(
                () -> new AssertionError(
                        "Expected the victim's cart item to still exist after attacker B's rejected increase call"));
        assertThat(afterIncrease.getQuantity())
                .as("attacker B's increase call must not have mutated the victim's real cart item")
                .isEqualTo(1);

        mockMvc.perform(delete("/api/cart/items/{cartItemId}", victimCartItemId).session(attackerSession))
                .andExpect(status().isNotFound());

        entityManager.flush();
        entityManager.clear();
        assertThat(cartItemRepository.findById(victimCartItemId))
                .as("attacker B's remove call must not have deleted victim A's real cart item row")
                .isPresent();
    }

    // ================================================================
    // Attacker request carries NO session at all - not even an anonymous one
    // -> CustomerAuthInterceptor answers 401 before the controller (or
    // CartService) ever runs.
    // ================================================================

    @Test
    void increaseAndRemove_withNoSessionAtAll_isUnauthorized_leavesVictimsCartItemUntouched() throws Exception {
        Customer victimTemplate = TestDataFactory.customer();
        MockHttpSession victimSession = registerAndLogin(victimTemplate);

        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long victimCartItemId = addToCartAndGetItemId(victimSession, victimTemplate.getEmail(), pizza);

        // No .session(...) call anywhere below - literally no session, not a
        // differently-scoped one.
        mockMvc.perform(post("/api/cart/items/{cartItemId}/increase", victimCartItemId))
                .andExpect(status().isUnauthorized());

        entityManager.flush();
        entityManager.clear();
        CartItem afterIncrease = cartItemRepository.findById(victimCartItemId).orElseThrow(
                () -> new AssertionError(
                        "Expected the victim's cart item to still exist after the unauthenticated increase call"));
        assertThat(afterIncrease.getQuantity())
                .as("an unauthenticated increase call must not have mutated the victim's real cart item")
                .isEqualTo(1);

        mockMvc.perform(delete("/api/cart/items/{cartItemId}", victimCartItemId))
                .andExpect(status().isUnauthorized());

        entityManager.flush();
        entityManager.clear();
        assertThat(cartItemRepository.findById(victimCartItemId))
                .as("a completely unauthenticated remove call must not have deleted victim A's real cart item row")
                .isPresent();
    }
}
