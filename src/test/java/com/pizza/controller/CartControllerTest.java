package com.pizza.controller;

import com.pizza.entity.Cart;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.service.CartService;
import com.pizza.service.CouponService;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link CartController}.
 *
 * <p>Bug #2 fix: {@code /cart/**}, {@code /increase/**} and {@code
 * /decrease/**} are now covered by {@code CustomerAuthInterceptor} (see
 * {@code WebMvcConfig}), and {@code removeItem}/{@code increaseQuantity}/
 * {@code decreaseQuantity} thread the logged-in customer's email down into
 * {@link CartService} so it can verify cart-item ownership. Since {@code
 * WebMvcTest} slices don't load {@code WebMvcConfig}'s interceptor
 * registration, the "no session" tests below assert the controller's own
 * explicit {@code SessionUtil} null-check (the defense-in-depth layer),
 * which fires the same {@code redirect:/login} outcome the interceptor would
 * produce in production.</p>
 */
@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @MockBean
    private CouponService couponService;

    private MockHttpSession customerSession(Customer customer) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_CUSTOMER, customer);
        return session;
    }

    // ------------------------------------------------------------ cart/add
    // (session-protected: confirmed by the explicit null-check in the controller)

    @Test
    void addToCart_withNoSession_redirectsToLogin_andNeverCallsCartService() throws Exception {
        mockMvc.perform(post("/cart/add").param("pizzaId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(cartService);
    }

    @Test
    void addToCart_withCustomerSession_addsItemAndRedirectsToPizzas() throws Exception {
        Customer customer = TestDataFactory.customer();

        mockMvc.perform(post("/cart/add")
                        .param("pizzaId", "42")
                        .session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pizzas"))
                .andExpect(flash().attribute("successMessage", "Pizza added to cart successfully!"));

        verify(cartService).addPizzaToCart(customer.getEmail(), 42L);
    }

    // ------------------------------------------------------------------ /cart
    // (session-protected: confirmed by the explicit null-check in the controller)

    @Test
    void viewCart_withNoSession_redirectsToLogin_andNeverCallsCartService() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(cartService);
    }

    @Test
    void viewCart_withCustomerSession_rendersCartView() throws Exception {
        Customer customer = TestDataFactory.customer();
        when(cartService.getCart(customer.getEmail()))
                .thenReturn(TestDataFactory.cart(customer.getEmail()));
        when(cartService.getCartSubtotal(customer.getEmail()))
                .thenReturn(BigDecimal.ZERO);

        mockMvc.perform(get("/cart").session(customerSession(customer)))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"));
    }

    @Test
    void viewCart_withCustomerSession_addsActiveCouponsToModel() throws Exception {
        Customer customer = TestDataFactory.customer();
        when(cartService.getCart(customer.getEmail()))
                .thenReturn(TestDataFactory.cart(customer.getEmail()));
        when(cartService.getCartSubtotal(customer.getEmail()))
                .thenReturn(BigDecimal.ZERO);
        List<Coupon> activeCoupons = List.of(TestDataFactory.coupon());
        when(couponService.findActiveCoupons()).thenReturn(activeCoupons);

        mockMvc.perform(get("/cart").session(customerSession(customer)))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attribute("activeCoupons", activeCoupons));
    }

    // ============================================================
    // Bug #2 fix: NO session attached to the request at all -> redirected to
    // /login and the service is never invoked (either by CustomerAuthInterceptor,
    // now registered for these paths in WebMvcConfig, or by the controller's
    // own SessionUtil null-check as a defense-in-depth backstop).
    // ============================================================

    @Test
    void removeItem_withZeroSessionAttached_redirectsToLogin_andNeverCallsCartService() throws Exception {
        mockMvc.perform(post("/cart/remove").param("cartItemId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(cartService);
    }

    @Test
    void increaseQuantity_withZeroSessionAttached_redirectsToLogin_andNeverCallsCartService() throws Exception {
        mockMvc.perform(post("/increase/{cartItemId}", 9L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(cartService);
    }

    @Test
    void decreaseQuantity_withZeroSessionAttached_redirectsToLogin_andNeverCallsCartService() throws Exception {
        mockMvc.perform(post("/decrease/{cartItemId}", 12L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(cartService);
    }

    @Test
    void applyCoupon_withZeroSessionAttached_redirectsToLogin_andNeverCallsCouponService() throws Exception {
        mockMvc.perform(post("/cart/apply-coupon").param("couponCode", "SAVE10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(couponService);
    }

    // ============================================================
    // Bug #2 fix: happy path with a valid customer session.
    // ============================================================

    @Test
    void removeItem_withCustomerSession_invokesCartServiceWithCustomerEmail() throws Exception {
        Customer customer = TestDataFactory.customer();

        mockMvc.perform(post("/cart/remove")
                        .param("cartItemId", "7")
                        .session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).removeItem(7L, customer.getEmail());
    }

    @Test
    void increaseQuantity_withCustomerSession_invokesCartServiceWithCustomerEmail() throws Exception {
        Customer customer = TestDataFactory.customer();

        mockMvc.perform(post("/increase/{cartItemId}", 9L).session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).increaseQuantity(9L, customer.getEmail());
    }

    @Test
    void increaseQuantity_withCustomerSession_flashesErrorMessage_whenCapExceeded() throws Exception {
        Customer customer = TestDataFactory.customer();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Maximum quantity per item is 50."))
                .when(cartService).increaseQuantity(9L, customer.getEmail());

        mockMvc.perform(post("/increase/{cartItemId}", 9L).session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("errorMessage", "Maximum quantity per item is 50."));
    }

    @Test
    void decreaseQuantity_withCustomerSession_invokesCartServiceWithCustomerEmail() throws Exception {
        Customer customer = TestDataFactory.customer();

        mockMvc.perform(post("/decrease/{cartItemId}", 12L).session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).decreaseQuantity(12L, customer.getEmail());
    }

    @Test
    void applyCoupon_withCustomerSession_invokesCouponService() throws Exception {
        Customer customer = TestDataFactory.customer();
        Coupon coupon = TestDataFactory.coupon();
        when(couponService.validateCoupon("SAVE10")).thenReturn(coupon);

        mockMvc.perform(post("/cart/apply-coupon")
                        .param("couponCode", "SAVE10")
                        .session(customerSession(customer)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("successMessage", "Coupon applied successfully!"));

        verify(couponService).validateCoupon("SAVE10");
    }

    // ---------------------------------------------------- Bug: coupon removal

    @Test
    void removeCoupon_clearsAppliedCouponFromSession_andFlashesSuccessMessage() throws Exception {
        Customer customer = TestDataFactory.customer();
        MockHttpSession session = customerSession(customer);
        session.setAttribute("appliedCoupon", TestDataFactory.coupon());

        mockMvc.perform(post("/cart/remove-coupon").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("successMessage", "Coupon removed."));

        org.assertj.core.api.Assertions.assertThat(session.getAttribute("appliedCoupon")).isNull();
    }
}
