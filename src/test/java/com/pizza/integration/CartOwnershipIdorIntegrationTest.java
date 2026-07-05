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

/**
 * The definitive end-to-end proof of the cart-ownership IDOR gap that {@code
 * CartControllerTest} (Task 5, {@code @WebMvcTest} slice) already documented
 * with mocked services. Reading {@link com.pizza.controller.CartController}
 * confirms: {@code POST /cart/remove}, {@code POST /increase/{cartItemId}}
 * and {@code POST /decrease/{cartItemId}} take no {@code HttpSession}
 * parameter at all and perform no ownership/authentication check whatsoever
 * - they call straight into {@link com.pizza.service.CartService} using only
 * the raw {@code cartItemId}, which any authenticated (or entirely
 * unauthenticated) caller can guess or enumerate.
 *
 * <p>This class proves the SAME gap end-to-end, against real H2-persisted
 * data across two genuinely separate, real customers (registered and logged
 * in through the real {@code /register}/{@code /login} endpoints, each with
 * its own {@link MockHttpSession}): customer A adds a real pizza to their
 * real cart, and a request that is either authenticated as a completely
 * different customer B, or carries no session at all, successfully mutates
 * and then deletes A's real {@link CartItem} row.
 *
 * <p>{@code entityManager.flush()}/{@code clear()} after each mutating call
 * forces Hibernate to synchronize with H2 and drop cached state, so every
 * assertion below reads back genuine database rows via {@link
 * CartItemRepository}, not merely mutated Java heap state from this test's
 * shared persistence context (the same technique already used in {@code
 * CustomerCheckoutFlowIntegrationTest} and {@code
 * AdminCouponManagementIntegrationTest} for the same reason).
 *
 * <p>These tests must fail loudly if the gap is ever "fixed" by adding an
 * ownership/auth check without updating the test to match - they assert the
 * controller currently succeeds and currently mutates the victim's data;
 * they must NOT be softened to assert a login redirect or a 403 that the
 * controller does not actually produce today.
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
    // Attacker is a real, separately logged-in customer B.
    // ================================================================

    @Test
    void increaseAndRemove_usingAnotherLoggedInCustomersSession_mutateAndDeleteVictimsRealCartItem() throws Exception {
        Customer victimTemplate = TestDataFactory.customer();
        MockHttpSession victimSession = registerAndLogin(victimTemplate);

        Customer attackerTemplate = TestDataFactory.customer();
        MockHttpSession attackerSession = registerAndLogin(attackerTemplate);

        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long victimCartItemId = addToCartAndGetItemId(victimSession, victimTemplate.getEmail(), pizza);

        // Attacker B is authenticated as THEMSELVES, not as the victim, and
        // targets the victim's real cartItemId directly.
        mockMvc.perform(post("/increase/{cartItemId}", victimCartItemId).session(attackerSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        entityManager.flush();
        entityManager.clear();
        CartItem afterIncrease = cartItemRepository.findById(victimCartItemId).orElseThrow(
                () -> new AssertionError("Expected the victim's cart item to still exist after attacker B's increase call"));
        assertThat(afterIncrease.getQuantity()).isEqualTo(2);

        mockMvc.perform(post("/cart/remove").param("cartItemId", String.valueOf(victimCartItemId))
                        .session(attackerSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        entityManager.flush();
        entityManager.clear();
        assertThat(cartItemRepository.findById(victimCartItemId))
                .as("attacker B's request deleted victim A's real cart item row")
                .isEmpty();
    }

    // ================================================================
    // Attacker request carries NO session at all - not even an anonymous one.
    // ================================================================

    @Test
    void increaseAndRemove_withNoSessionAtAll_mutateAndDeleteVictimsRealCartItem() throws Exception {
        Customer victimTemplate = TestDataFactory.customer();
        MockHttpSession victimSession = registerAndLogin(victimTemplate);

        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long victimCartItemId = addToCartAndGetItemId(victimSession, victimTemplate.getEmail(), pizza);

        // No .session(...) call anywhere below - literally no session, not a
        // differently-scoped one.
        mockMvc.perform(post("/increase/{cartItemId}", victimCartItemId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        entityManager.flush();
        entityManager.clear();
        CartItem afterIncrease = cartItemRepository.findById(victimCartItemId).orElseThrow(
                () -> new AssertionError("Expected the victim's cart item to still exist after the unauthenticated increase call"));
        assertThat(afterIncrease.getQuantity()).isEqualTo(2);

        mockMvc.perform(post("/cart/remove").param("cartItemId", String.valueOf(victimCartItemId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        entityManager.flush();
        entityManager.clear();
        assertThat(cartItemRepository.findById(victimCartItemId))
                .as("a completely unauthenticated request deleted victim A's real cart item row")
                .isEmpty();
    }
}
