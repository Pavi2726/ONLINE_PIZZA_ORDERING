package com.pizza.controller;

import com.pizza.dto.LoginRequest;
import com.pizza.entity.Admin;
import com.pizza.exception.InvalidCredentialsException;
import com.pizza.service.AdminService;
import com.pizza.service.PizzaService;
import com.pizza.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin authentication and dashboard. Pizza management lives in
 * {@link AdminPizzaController}; this controller only handles login, logout and
 * the dashboard overview. All routes (except login/logout) are protected by
 * {@code AdminAuthInterceptor}.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final PizzaService pizzaService;

    /** Redirect the bare /admin path to a sensible place. */
    @GetMapping
    public String index(HttpSession session) {
        return SessionUtil.isAdminLoggedIn(session)
                ? "redirect:/admin/dashboard"
                : "redirect:/admin/login";
    }

    @GetMapping("/login")
    public String showLogin(HttpSession session, Model model) {
        if (SessionUtil.isAdminLoggedIn(session)) {
            return "redirect:/admin/dashboard";
        }
        if (!model.containsAttribute("loginRequest")) {
            model.addAttribute("loginRequest", new LoginRequest());
        }
        return "admin-login";
    }

    @PostMapping("/login")
    public String login(
            @Valid @ModelAttribute("loginRequest") LoginRequest loginRequest,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "admin-login";
        }
        try {
            Admin admin = adminService.login(loginRequest.getEmail(), loginRequest.getPassword());
            SessionUtil.loginAdmin(session, admin);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Welcome, " + admin.getName() + "!");
            return "redirect:/admin/dashboard";
        } catch (InvalidCredentialsException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "admin-login";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalPizzas", pizzaService.countAll());
        model.addAttribute("availablePizzas", pizzaService.countAvailable());
        model.addAttribute("outOfStockPizzas", pizzaService.countOutOfStock());
        return "admin-dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        SessionUtil.logoutAdmin(session);
        redirectAttributes.addFlashAttribute("successMessage", "You have been logged out.");
        return "redirect:/admin/login";
    }
}
