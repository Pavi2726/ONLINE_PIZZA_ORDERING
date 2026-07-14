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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the full customer checkout flow (US-001/US-002 login, cart,
 * coupon, US-007 order placement): register -> login -> add pizzas to cart -> apply a
 * real coupon -> review the cart -> place. Every hop is a real {@code MockMvc} call
 * through the real API controllers, services and repositories against H2; only
 * {@code CloudinaryService} is mocked (inherited, and unused by this flow). The
 * assertions verify the persisted {@link Order}'s totals in H2 match the full
 * subtotal/discount/tax/total pipeline, not just isolated arithmetic.
 */
class CustomerCheckoutFlowIntegrationTest extends AbstractIntegrationTest {

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

    private void addToCart(MockHttpSession session, Pizza pizza) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pizzaId", pizza.getId())))
                        .session(session))
                .andExpect(status().isOk());
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
        addToCart(session, pizza1);
        addToCart(session, pizza2);
        addToCart(session, pizza2);

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

        // Applying the coupon answers with the whole recomputed cart, so the client
        // renders the discount without a follow-up read.
        mockMvc.perform(post("/api/cart/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("couponCode", coupon.getCouponCode())))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Coupon applied successfully!"))
                .andExpect(jsonPath("$.data.appliedCoupon.couponCode").value(coupon.getCouponCode()))
                .andExpect(jsonPath("$.data.subtotal").value(20.00))
                .andExpect(jsonPath("$.data.discount").value(2.00))
                .andExpect(jsonPath("$.data.grandTotal").value(18.00));

        // The checkout screen is backed by this same cart read - there is no separate endpoint.
        mockMvc.perform(get("/api/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        MvcResult placeResult = mockMvc.perform(post("/api/orders").session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Order placed successfully!"))
                .andExpect(jsonPath("$.data.status").value("PLACED"))
                .andReturn();

        String orderNumber = objectMapper
                .readTree(placeResult.getResponse().getContentAsString())
                .path("data").path("orderNumber").asText();
        assertThat(orderNumber).isNotBlank();

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

        // Placing the order clears the cart for real in H2.
        Cart cartAfterCheckout = cartRepository.findByUsername(template.getEmail()).orElseThrow();
        assertThat(cartAfterCheckout.getCartItems()).isEmpty();
    }

    @Test
    void viewCart_offersActiveCouponButNotInactiveOne() throws Exception {
        Customer template = TestDataFactory.customer();
        MockHttpSession session = registerAndLogin(template);
        assertThat(session).isNotNull();

        Pizza pizza = pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Margherita", new BigDecimal("10.00"), "Classic", true));
        addToCart(session, pizza);

        // Same stale-collection issue documented above in fullCheckoutFlow...: the
        // Cart's cartItems were already cached (empty) by this shared transaction
        // before add-to-cart persisted its CartItem row directly via
        // CartItemRepository, so flush + clear before the next read.
        entityManager.flush();
        entityManager.clear();

        Coupon activeCoupon = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        Coupon inactiveCoupon = couponRepository.saveAndFlush(TestDataFactory.coupon(15, false));

        MvcResult result = mockMvc.perform(get("/api/cart").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(activeCoupon.getCouponCode());
        assertThat(body).doesNotContain(inactiveCoupon.getCouponCode());
    }

    /** An unknown code is rejected and must not leave a stale coupon applied to the cart. */
    @Test
    void applyCoupon_withUnknownCode_isRejectedAndClearsAnyPreviouslyAppliedCoupon() throws Exception {
        Customer template = TestDataFactory.customer();
        MockHttpSession session = registerAndLogin(template);

        Pizza pizza = pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Margherita", new BigDecimal("10.00"), "Classic", true));
        addToCart(session, pizza);
        entityManager.flush();
        entityManager.clear();

        Coupon valid = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        mockMvc.perform(post("/api/cart/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("couponCode", valid.getCouponCode())))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedCoupon.couponCode").value(valid.getCouponCode()));

        mockMvc.perform(post("/api/cart/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("couponCode", "NOPE-DOES-NOT-EXIST")))
                        .session(session))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCoupon").doesNotExist())
                .andExpect(jsonPath("$.discount").value(0));
    }

    /**
     * Logging out drops the applied coupon along with the principal. The server-rendered
     * app removed only the principal, so a coupon applied by one customer survived into
     * the next customer's session on the same browser.
     */
    @Test
    void logout_clearsTheAppliedCoupon_soItDoesNotLeakIntoTheNextCustomerOnTheSameSession() throws Exception {
        Customer first = TestDataFactory.customer();
        MockHttpSession session = registerAndLogin(first);

        Pizza pizza = pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Margherita", new BigDecimal("10.00"), "Classic", true));
        addToCart(session, pizza);
        entityManager.flush();
        entityManager.clear();

        Coupon coupon = couponRepository.saveAndFlush(TestDataFactory.coupon(50, true));
        mockMvc.perform(post("/api/cart/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("couponCode", coupon.getCouponCode())))
                        .session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());

        // A second customer logs in on the very same session.
        Customer second = TestDataFactory.customer();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", second.getFirstName(),
                                "lastName", second.getLastName(),
                                "email", second.getEmail(),
                                "phone", second.getPhone(),
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD,
                                "address", second.getAddress()))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", second.getEmail(), "password", PASSWORD)))
                        .session(session))
                .andExpect(status().isOk());

        addToCart(session, pizza);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCoupon").doesNotExist())
                .andExpect(jsonPath("$.discount").value(0));
    }
}
