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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The definitive end-to-end proof that the cart-ownership IDOR (Bug #2) is
 * now closed. {@link com.pizza.controller.CartController}'s {@code POST
 * /cart/remove}, {@code POST /increase/{cartItemId}} and {@code POST
 * /decrease/{cartItemId}} now thread the caller's customer email down into
 * {@link com.pizza.service.CartService}, which looks the cart item up scoped
 * to that username via {@code CartItemRepository.findByIdAndCart_Username}
 * - so an id belonging to a different customer's cart is rejected with a
 * 404, and {@code /cart/**}/{@code /increase/**}/{@code /decrease/**} are now
 * covered by {@code CustomerAuthInterceptor} (see {@code WebMvcConfig}), so
 * an unauthenticated request never reaches the controller at all.
 *
 * <p>This class proves the fix end-to-end, against real H2-persisted data
 * across two genuinely separate, real customers (registered and logged in
 * through the real {@code /register}/{@code /login} endpoints, each with its
 * own {@link MockHttpSession}): customer A adds a real pizza to their real
 * cart, and a request either authenticated as a completely different
 * customer B, or carrying no session at all, leaves A's real {@link
 * CartItem} row completely untouched.
 *
 * <p>{@code entityManager.flush()}/{@code clear()} after each mutating call
 * forces Hibernate to synchronize with H2 and drop cached state, so every
 * assertion below reads back genuine database rows via {@link
 * CartItemRepository}, not merely mutated Java heap state from this test's
 * shared persistence context (the same technique already used in {@code
 * CustomerCheckoutFlowIntegrationTest} and {@code
 * AdminCouponManagementIntegrationTest} for the same reason).
 */
class CartOwnershipIdorIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PizzaRepository pizzaRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private EntityManager entityManager;

    private MockHttpSession registerAndLogin(Customer template) throws Exception {
        mockMvc.perform(post("/register")
                        .param("firstName", template.getFirstName())
                        .param("lastName", template.getLastName())
                        .param("email", template.getEmail())
                        .param("phone", template.getPhone())
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD)
                        .param("address", template.getAddress()))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("email", template.getEmail())
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    /** A adds {@code pizza} to their real cart and returns the real, H2-persisted cartItemId. */
    private Long addToCartAndGetItemId(MockHttpSession victimSession, String victimEmail, Pizza pizza) throws Exception {
        mockMvc.perform(post("/cart/add")
                        .param("pizzaId", String.valueOf(pizza.getId()))
                        .session(victimSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pizzas"));

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
    void increaseAndRemove_usingAnotherLoggedInCustomersSession_failsWithNotFound_leavesVictimsCartItemUntouched() throws Exception {
        Customer victimTemplate = TestDataFactory.customer();
        MockHttpSession victimSession = registerAndLogin(victimTemplate);

        Customer attackerTemplate = TestDataFactory.customer();
        MockHttpSession attackerSession = registerAndLogin(attackerTemplate);

        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long victimCartItemId = addToCartAndGetItemId(victimSession, victimTemplate.getEmail(), pizza);

        // Attacker B is authenticated as THEMSELVES, not as the victim, and
        // targets the victim's real cartItemId directly.
        mockMvc.perform(post("/increase/{cartItemId}", victimCartItemId).session(attackerSession))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"));

        entityManager.flush();
        entityManager.clear();
        CartItem afterIncrease = cartItemRepository.findById(victimCartItemId).orElseThrow(
                () -> new AssertionError("Expected the victim's cart item to still exist after attacker B's rejected increase call"));
        assertThat(afterIncrease.getQuantity())
                .as("attacker B's increase call must not have mutated the victim's real cart item")
                .isEqualTo(1);

        mockMvc.perform(post("/cart/remove").param("cartItemId", String.valueOf(victimCartItemId))
                        .session(attackerSession))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"));

        entityManager.flush();
        entityManager.clear();
        assertThat(cartItemRepository.findById(victimCartItemId))
                .as("attacker B's remove call must not have deleted victim A's real cart item row")
                .isPresent();
    }

    // ================================================================
    // Attacker request carries NO session at all - not even an anonymous one
    // -> CustomerAuthInterceptor now redirects to /login before the
    // controller (or CartService) ever runs.
    // ================================================================

    @Test
    void increaseAndRemove_withNoSessionAtAll_redirectsToLogin_leavesVictimsCartItemUntouched() throws Exception {
        Customer victimTemplate = TestDataFactory.customer();
        MockHttpSession victimSession = registerAndLogin(victimTemplate);

        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long victimCartItemId = addToCartAndGetItemId(victimSession, victimTemplate.getEmail(), pizza);

        // No .session(...) call anywhere below - literally no session, not a
        // differently-scoped one.
        mockMvc.perform(post("/increase/{cartItemId}", victimCartItemId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        entityManager.flush();
        entityManager.clear();
        CartItem afterIncrease = cartItemRepository.findById(victimCartItemId).orElseThrow(
                () -> new AssertionError("Expected the victim's cart item to still exist after the unauthenticated increase call"));
        assertThat(afterIncrease.getQuantity())
                .as("an unauthenticated increase call must not have mutated the victim's real cart item")
                .isEqualTo(1);

        mockMvc.perform(post("/cart/remove").param("cartItemId", String.valueOf(victimCartItemId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        entityManager.flush();
        entityManager.clear();
        assertThat(cartItemRepository.findById(victimCartItemId))
                .as("a completely unauthenticated remove call must not have deleted victim A's real cart item row")
                .isPresent();
    }
}
