package com.pizza.api;

import com.pizza.api.dto.ApiResponses.CartResponse;
import com.pizza.api.dto.ApiResponses.Envelope;
import com.pizza.entity.Cart;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.service.CartService;
import com.pizza.service.CouponService;
import com.pizza.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart, including coupon apply/remove. Guarded by {@code CustomerAuthInterceptor}.
 *
 * <p>Every mutation answers with the whole refreshed {@link CartResponse}, so the cart
 * page and the navbar badge both re-render from the mutation's own response and never
 * need a follow-up GET.</p>
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartApiController {

    private final CartService cartService;
    private final CouponService couponService;

    public record AddItemRequest(Long pizzaId) {
    }

    public record AddDrinkRequest(Long drinkId) {
    }

    public record ApplyCouponRequest(String couponCode) {
    }

    @GetMapping
    public CartResponse view(HttpSession session) {
        return currentCart(session);
    }

    @PostMapping("/items")
    public Envelope<CartResponse> addItem(@RequestBody AddItemRequest request, HttpSession session) {
        cartService.addPizzaToCart(customer(session).getEmail(), request.pizzaId());
        return Envelope.success("Pizza added to cart successfully!", currentCart(session));
    }

    @PostMapping("/drinks")
    public Envelope<CartResponse> addDrink(@RequestBody AddDrinkRequest request, HttpSession session) {
        cartService.addDrinkToCart(customer(session).getEmail(), request.drinkId());
        return Envelope.success("Drink added to cart successfully!", currentCart(session));
    }

    @DeleteMapping("/items/{cartItemId}")
    public Envelope<CartResponse> removeItem(@PathVariable Long cartItemId, HttpSession session) {
        cartService.removeItem(cartItemId, customer(session).getEmail());
        return Envelope.success("Item removed from cart.", currentCart(session));
    }

    @PostMapping("/items/{cartItemId}/increase")
    public Envelope<CartResponse> increase(@PathVariable Long cartItemId, HttpSession session) {
        cartService.increaseQuantity(cartItemId, customer(session).getEmail());
        return Envelope.success("Cart updated.", currentCart(session));
    }

    @PostMapping("/items/{cartItemId}/decrease")
    public Envelope<CartResponse> decrease(@PathVariable Long cartItemId, HttpSession session) {
        cartService.decreaseQuantity(cartItemId, customer(session).getEmail());
        return Envelope.success("Cart updated.", currentCart(session));
    }

    /** An invalid or expired code clears any previously applied coupon, as before. */
    @PostMapping("/coupon")
    public Envelope<CartResponse> applyCoupon(@RequestBody ApplyCouponRequest request, HttpSession session) {
        try {
            Coupon coupon = couponService.validateCoupon(request.couponCode());
            session.setAttribute(SessionKeys.APPLIED_COUPON, coupon);
        } catch (RuntimeException ex) {
            session.removeAttribute(SessionKeys.APPLIED_COUPON);
            throw ex;
        }
        return Envelope.success("Coupon applied successfully!", currentCart(session));
    }

    @DeleteMapping("/coupon")
    public Envelope<CartResponse> removeCoupon(HttpSession session) {
        session.removeAttribute(SessionKeys.APPLIED_COUPON);
        return Envelope.success("Coupon removed.", currentCart(session));
    }

    private Customer customer(HttpSession session) {
        return SessionUtil.getCurrentCustomer(session);
    }

    /**
     * Assembles the cart view — items, the session coupon, and the three totals. Mirrors
     * what the old cart controller assembled into the model.
     */
    private CartResponse currentCart(HttpSession session) {
        Customer customer = customer(session);
        Cart cart = cartService.getCart(customer.getEmail());
        BigDecimal subtotal = cartService.getCartSubtotal(customer.getEmail());

        Coupon coupon = (Coupon) session.getAttribute(SessionKeys.APPLIED_COUPON);
        BigDecimal discount = BigDecimal.ZERO;
        if (coupon != null) {
            discount = subtotal
                    .multiply(BigDecimal.valueOf(coupon.getDiscountPercentage()))
                    // The old code divided without a scale, which throws ArithmeticException on
                    // any non-terminating quotient (e.g. a 3% discount). Rounding to paise here.
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        return new CartResponse(
                cart.getCartItems().stream().map(ApiMappers::cartItem).toList(),
                subtotal,
                discount,
                subtotal.subtract(discount),
                ApiMappers.coupon(coupon),
                ApiMappers.coupons(couponService.findActiveCoupons()),
                cartService.getItemCount(customer.getEmail()));
    }
}
