package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Cart;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
import com.pizza.repository.CartRepository;
import com.pizza.repository.CouponRepository;
import com.pizza.repository.OrderRepository;
import com.pizza.repository.PizzaRepository;
import com.pizza.testsupport.TestDataFactory;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end coverage of the full customer checkout flow (US-001/US-002 login,
 * cart, coupon, US-007 order placement): register -> login -> add pizzas to
 * cart -> apply a real coupon -> checkout -> place. Every hop is a real
 * {@code MockMvc} call through the real controllers, services and repositories
 * against H2; only {@code CloudinaryService} is mocked (inherited, and unused
 * by this flow). The assertions verify the persisted {@link Order}'s totals in
 * H2 match the full subtotal/discount/tax/total pipeline, not just isolated
 * arithmetic.
 */
class CustomerCheckoutFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PizzaRepository pizzaRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    private static final String PASSWORD = "Passw0rd!";

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

    @Test
    void fullCheckoutFlow_withRealCoupon_persistsOrderWithCorrectEndToEndTotals() throws Exception {
        Customer template = TestDataFactory.customer();
        MockHttpSession session = registerAndLogin(template);
        assertThat(session).isNotNull();

        Pizza pizza1 = pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Margherita", new BigDecimal("10.00"), "Classic", true));
        Pizza pizza2 = pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Pepperoni", new BigDecimal("5.00"), "Classic", true));

        // pizza1 x1, pizza2 x2 (two separate add-to-cart calls accumulate quantity).
        mockMvc.perform(post("/cart/add").param("pizzaId", String.valueOf(pizza1.getId())).session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/cart/add").param("pizzaId", String.valueOf(pizza2.getId())).session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/cart/add").param("pizzaId", String.valueOf(pizza2.getId())).session(session))
                .andExpect(status().is3xxRedirection());

        // CartService.addPizzaToCart saves each CartItem through CartItemRepository
        // directly, never appending to the already-loaded parent Cart's in-memory
        // (eagerly-fetched) collection. That collection was cached the moment this
        // shared test transaction first loaded the Cart, so without clearing the
        // persistence context here, every later read in this same transaction
        // (including the application's own CartService.getCart() calls during
        // checkout/place) would keep seeing that first stale, empty collection
        // instead of the two CartItem rows genuinely persisted in H2. Real,
        // separate HTTP requests never hit this because each gets its own
        // fresh persistence context; it only shows up here because one
        // @Transactional test method shares a single Hibernate session across
        // every MockMvc call. flush() first so the pending dirty-checked
        // quantity increment (from re-adding pizza2) is written to H2 before
        // clear() detaches everything - clear() alone would silently drop
        // any not-yet-flushed change.
        entityManager.flush();
        entityManager.clear();

        Cart persistedCart = cartRepository.findByUsername(template.getEmail()).orElseThrow();
        assertThat(persistedCart.getCartItems()).hasSize(2);
        BigDecimal cartSubtotal = persistedCart.getCartItems().stream()
                .map(item -> item.getPizza().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(cartSubtotal).isEqualByComparingTo("20.00");

        Coupon coupon = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));

        mockMvc.perform(post("/cart/apply-coupon").param("couponCode", coupon.getCouponCode()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "Coupon applied successfully!"));

        mockMvc.perform(get("/orders/checkout").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"));

        MvcResult placeResult = mockMvc.perform(post("/orders/place").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "Order placed successfully!"))
                .andReturn();

        String redirectedUrl = placeResult.getResponse().getRedirectedUrl();
        assertThat(redirectedUrl).startsWith("/orders/success/");
        String orderNumber = redirectedUrl.substring("/orders/success/".length());

        Order persistedOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();

        // subtotal 20.00 (10*1 + 5*2); 10% coupon discount = 2.00;
        // discounted subtotal 18.00; 8% tax = 1.44; total = 19.44.
        assertThat(persistedOrder.getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(persistedOrder.getDiscountAmount()).isEqualByComparingTo("2.00");
        assertThat(persistedOrder.getDiscountPercentage()).isEqualTo(10);
        assertThat(persistedOrder.getCouponCode()).isEqualTo(coupon.getCouponCode());
        assertThat(persistedOrder.getTax()).isEqualByComparingTo("1.44");
        assertThat(persistedOrder.getTotalAmount()).isEqualByComparingTo("19.44");
        assertThat(persistedOrder.getStatus()).isEqualTo("PLACED");
        assertThat(persistedOrder.getDeliveryAddress()).isEqualTo(template.getAddress());
        assertThat(persistedOrder.getOrderItems()).hasSize(2);

        // Checkout clears the cart for real in H2.
        Cart cartAfterCheckout = cartRepository.findByUsername(template.getEmail()).orElseThrow();
        assertThat(cartAfterCheckout.getCartItems()).isEmpty();
    }

    @Test
    void viewCart_rendersActiveCouponCodeButNotInactiveOne() throws Exception {
        Customer template = TestDataFactory.customer();
        MockHttpSession session = registerAndLogin(template);
        assertThat(session).isNotNull();

        Pizza pizza = pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Margherita", new BigDecimal("10.00"), "Classic", true));
        mockMvc.perform(post("/cart/add").param("pizzaId", String.valueOf(pizza.getId())).session(session))
                .andExpect(status().is3xxRedirection());

        // Same stale-collection issue documented above in fullCheckoutFlow...: the
        // Cart's cartItems were already cached (empty) by this shared transaction
        // before add-to-cart persisted its CartItem row directly via
        // CartItemRepository, so flush + clear before the next read.
        entityManager.flush();
        entityManager.clear();

        Coupon activeCoupon = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        Coupon inactiveCoupon = couponRepository.saveAndFlush(TestDataFactory.coupon(15, false));

        MvcResult result = mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(activeCoupon.getCouponCode());
        assertThat(body).doesNotContain(inactiveCoupon.getCouponCode());
    }
}
