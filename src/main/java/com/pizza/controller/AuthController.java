package com.pizza.controller;

import com.pizza.dto.LoginRequest;
import com.pizza.dto.RegisterRequest;
import com.pizza.entity.Customer;
import com.pizza.exception.DuplicateEmailException;
import com.pizza.exception.DuplicatePhoneException;
import com.pizza.exception.InvalidCredentialsException;
import com.pizza.service.CustomerService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles customer registration, login and logout (US-001, US-002) using simple
 * local authentication with BCrypt and session storage.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final CustomerService customerService;

    @GetMapping("/register")
    public String showRegister(Model model) {
        if (!model.containsAttribute("registerRequest")) {
            model.addAttribute("registerRequest", new RegisterRequest());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registerRequest") RegisterRequest registerRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!registerRequest.isPasswordConfirmed()) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "Passwords do not match");
        }
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            customerService.register(registerRequest);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Registration successful. Please login.");
            return "redirect:/login";
        } catch (DuplicateEmailException | DuplicatePhoneException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "register";
        }
    }

    @GetMapping("/login")
    public String showLogin(Model model) {
        if (!model.containsAttribute("loginRequest")) {
            model.addAttribute("loginRequest", new LoginRequest());
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @Valid @ModelAttribute("loginRequest") LoginRequest loginRequest,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "login";
        }

        try {
            Customer customer = customerService.login(
                    loginRequest.getEmail(), loginRequest.getPassword());
            SessionUtil.loginCustomer(session, customer);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Welcome back, " + customer.getFirstName() + "!");
            return "redirect:/";
        } catch (InvalidCredentialsException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        SessionUtil.logoutCustomer(session);
        redirectAttributes.addFlashAttribute("successMessage", "You have been logged out.");
        return "redirect:/login";
    }
}
