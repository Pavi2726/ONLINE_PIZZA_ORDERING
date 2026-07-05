package com.pizza.controller;

import com.pizza.entity.Cart;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.service.CartService;
import com.pizza.service.CouponService;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link CartController}.
 *
 * <p>{@code POST /cart/add} and {@code GET /cart} read the customer principal
 * from the {@link jakarta.servlet.http.HttpSession} themselves via
 * {@link SessionUtil} and redirect to {@code /login} when it is absent -
 * those two routes are session-protected and are covered first below as a
 * baseline.
 *
 * <p><b>The characterization tests below are the important part of this
 * class.</b> Reading {@code CartController} shows that {@code POST
 * /cart/remove}, {@code POST /increase/{cartItemId}} and {@code POST
 * /decrease/{cartItemId}} take no {@code HttpSession} parameter at all and
 * perform no {@link SessionUtil} check whatsoever - they call straight into
 * {@link CartService} using only the raw path/request parameter. {@code POST
 * /cart/apply-coupon} does declare an {@code HttpSession} parameter, but only
 * uses it to stash the applied coupon; it never checks who (if anyone) is
 * logged in. This is a confirmed, real authorization gap: any anonymous
 * caller who guesses or enumerates a {@code cartItemId} can mutate a
 * different customer's cart with no login at all. These tests issue the
 * requests with <b>no {@code .session(...)} call whatsoever</b> - not a
 * differently-scoped session, literally none - and assert the controller
 * still succeeds and still invokes the mocked service. They exist to
 * document this gap precisely; they must fail loudly if someone "fixes" the
 * gap by adding an auth check without updating the test to match, and must
 * NOT be softened to assert a login redirect that the controller does not
 * actually perform.
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

    // ============================================================
    // Characterization tests: NO session attached to the request at all.
    // ============================================================

    @Test
    void removeItem_withZeroSessionAttached_stillSucceeds_andInvokesCartService() throws Exception {
        // No .session(...) call anywhere in this request.
        mockMvc.perform(post("/cart/remove").param("cartItemId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).removeItem(7L);
    }

    @Test
    void increaseQuantity_withZeroSessionAttached_stillSucceeds_andInvokesCartService() throws Exception {
        mockMvc.perform(post("/increase/{cartItemId}", 9L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).increaseQuantity(9L);
    }

    @Test
    void decreaseQuantity_withZeroSessionAttached_stillSucceeds_andInvokesCartService() throws Exception {
        mockMvc.perform(post("/decrease/{cartItemId}", 12L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).decreaseQuantity(12L);
    }

    @Test
    void applyCoupon_withZeroSessionAttached_stillSucceeds_andInvokesCouponService() throws Exception {
        // applyCoupon does declare an HttpSession parameter, but only to stash
        // the coupon - Spring auto-vivifies a brand-new, anonymous session for
        // this request (request.getSession(true)); no login is ever checked.
        Coupon coupon = TestDataFactory.coupon();
        when(couponService.validateCoupon("SAVE10")).thenReturn(coupon);

        mockMvc.perform(post("/cart/apply-coupon").param("couponCode", "SAVE10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("successMessage", "Coupon applied successfully!"));

        verify(couponService).validateCoupon("SAVE10");
    }
}
