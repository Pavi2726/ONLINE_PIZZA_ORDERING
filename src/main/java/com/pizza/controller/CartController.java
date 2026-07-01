package com.pizza.controller;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pizza.entity.Cart;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.service.CartService;
import com.pizza.service.CouponService;
import com.pizza.util.SessionUtil;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
private final CouponService couponService;
    @PostMapping("/cart/add")
    public String addToCart(@RequestParam Long pizzaId,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        Customer customer = SessionUtil.getCurrentCustomer(session);

        if (customer == null) {
            return "redirect:/login";
        }

        cartService.addPizzaToCart(customer.getEmail(), pizzaId);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Pizza added to cart successfully!"
        );

        return "redirect:/pizzas";
    }
   @GetMapping("/cart")
public String viewCart(HttpSession session, Model model) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        return "redirect:/login";
    }

    Cart cart = cartService.getCart(customer.getEmail());

    model.addAttribute("cart", cart);

    BigDecimal subtotal = cartService.getCartSubtotal(customer.getEmail());

    model.addAttribute("subtotal", subtotal);

    Coupon coupon = (Coupon) session.getAttribute("appliedCoupon");

    BigDecimal discount = BigDecimal.ZERO;

    if (coupon != null) {

        discount = subtotal
                .multiply(BigDecimal.valueOf(coupon.getDiscountPercentage()))
                .divide(BigDecimal.valueOf(100));

        model.addAttribute("coupon", coupon);
    }

    BigDecimal grandTotal = subtotal.subtract(discount);

    model.addAttribute("discount", discount);
    model.addAttribute("grandTotal", grandTotal);

    return "cart";
}
@PostMapping("/cart/remove")
public String removeItem(@RequestParam Long cartItemId) {

    System.out.println("REMOVE ITEM ID = " + cartItemId);

    cartService.removeItem(cartItemId);

    return "redirect:/cart";
}
@PostMapping("/increase/{cartItemId}")
public String increaseQuantity(@PathVariable Long cartItemId) {

    cartService.increaseQuantity(cartItemId);

    return "redirect:/cart";
}
@PostMapping("/decrease/{cartItemId}")
public String decreaseQuantity(@PathVariable Long cartItemId) {

    cartService.decreaseQuantity(cartItemId);

    return "redirect:/cart";
}
@PostMapping("/cart/apply-coupon")
public String applyCoupon(@RequestParam String couponCode,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

    try {

        Coupon coupon = couponService.validateCoupon(couponCode);

        // Save coupon in session
        session.setAttribute("appliedCoupon", coupon);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Coupon applied successfully!"
        );

    } catch (RuntimeException e) {

        session.removeAttribute("appliedCoupon");

        redirectAttributes.addFlashAttribute(
                "errorMessage",
                e.getMessage()
        );
    }

    return "redirect:/cart";
}
}