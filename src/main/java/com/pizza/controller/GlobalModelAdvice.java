package com.pizza.controller;

import com.pizza.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the logged-in principals (customer / admin) to every Thymeleaf view.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("currentCustomer")
    public Object currentCustomer(HttpSession session) {
        return SessionUtil.getCurrentCustomer(session);
    }

    @ModelAttribute("currentAdmin")
    public Object currentAdmin(HttpSession session) {
        return SessionUtil.getCurrentAdmin(session);
    }
}
