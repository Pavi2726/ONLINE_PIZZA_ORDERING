package com.pizza.api;

import com.pizza.api.dto.ApiResponses.CustomerResponse;
import com.pizza.api.dto.ApiResponses.Envelope;
import com.pizza.api.dto.ApiResponses.LoginResponse;
import com.pizza.api.dto.ApiResponses.MeResponse;
import com.pizza.dto.LoginRequest;
import com.pizza.dto.RegisterRequest;
import com.pizza.entity.Customer;
import com.pizza.service.CartService;
import com.pizza.service.CustomerService;
import com.pizza.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer registration, login, logout, and the SPA's session bootstrap probe. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthApiController {

    private final CustomerService customerService;
    private final CartService cartService;

    /**
     * Session bootstrap. Unguarded and always 200 — the SPA calls this on every load
     * to decide whether it is logged in, so "logged out" is a normal answer, not an error.
     * Successor to {@code GlobalModelAdvice}.
     */
    @GetMapping("/me")
    public MeResponse me(HttpSession session) {
        Customer customer = SessionUtil.getCurrentCustomer(session);
        return new MeResponse(
                ApiMappers.customer(customer),
                ApiMappers.admin(SessionUtil.getCurrentAdmin(session)),
                customer == null ? 0 : cartService.getItemCount(customer.getEmail()));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<Envelope<CustomerResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            BindingResult bindingResult) throws BindException {

        if (!request.isPasswordConfirmed()) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
        }
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }

        Customer customer = customerService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                Envelope.success("Registration successful. Please login.", ApiMappers.customer(customer)));
    }

    @PostMapping("/auth/login")
    public Envelope<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        Customer customer = customerService.login(request.getEmail(), request.getPassword());
        SessionUtil.loginCustomer(session, customer);
        LoginResponse body = new LoginResponse(
                ApiMappers.customer(customer), cartService.getItemCount(customer.getEmail()));
        return Envelope.success("Welcome back, " + customer.getFirstName() + "!", body);
    }

    /**
     * Also drops the applied coupon. The server-rendered logout removed only the
     * customer principal, so a coupon applied by one customer survived into the next
     * session on the same browser.
     */
    @PostMapping("/auth/logout")
    public Envelope<Map<String, Object>> logout(HttpSession session) {
        SessionUtil.logoutCustomer(session);
        session.removeAttribute(SessionKeys.APPLIED_COUPON);
        return Envelope.success("You have been logged out.", null);
    }
}
