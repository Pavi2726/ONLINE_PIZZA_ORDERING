package com.pizza.controller;

import com.pizza.entity.Customer;
import com.pizza.service.CartService;
import com.pizza.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the logged-in principals (customer / admin) to every Thymeleaf view.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final CartService cartService;

    @ModelAttribute("currentCustomer")
    public Object currentCustomer(HttpSession session) {
        return SessionUtil.getCurrentCustomer(session);
    }

    @ModelAttribute("currentAdmin")
    public Object currentAdmin(HttpSession session) {
        return SessionUtil.getCurrentAdmin(session);
    }

    /**
     * Total quantity across the logged-in customer's cart, for the navbar
     * cart badge. Must not invoke {@link CartService} at all when there is no
     * logged-in customer, so admin/login/register pages never trigger a
     * cart-count DB query.
     */
    @ModelAttribute("cartItemCount")
    public Integer cartItemCount(HttpSession session) {
        Customer currentCustomer = SessionUtil.getCurrentCustomer(session);
        if (currentCustomer == null) {
            return 0;
        }
        return cartService.getItemCount(currentCustomer.getEmail());
    }
}
