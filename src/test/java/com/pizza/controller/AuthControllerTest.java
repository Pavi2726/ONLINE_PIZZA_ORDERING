package com.pizza.controller;

import com.pizza.entity.Customer;
import com.pizza.exception.DuplicateEmailException;
import com.pizza.exception.DuplicatePhoneException;
import com.pizza.exception.InvalidCredentialsException;
import com.pizza.service.CartService;
import com.pizza.service.CustomerService;
import com.pizza.testsupport.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link AuthController} (US-001/US-002).
 * Focuses on the two failure paths that must never turn into a 500: a
 * register submission whose passwords don't match, and a register/login
 * submission where {@link CustomerService} throws one of its checked
 * business exceptions.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    // GlobalModelAdvice (loaded in every @WebMvcTest slice) now depends on
    // CartService for the navbar cart-badge model attribute; login/register
    // pages have no logged-in customer so it's never actually invoked, but
    // the bean must still exist for the ApplicationContext to start.
    @MockBean
    private CartService cartService;

    // ------------------------------------------------------------- register

    @Test
    void showRegister_rendersFormWithEmptyCommandObject() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("registerRequest"));
    }

    @Test
    void register_withMismatchedPasswords_reRendersFormWithFieldError_notA500() throws Exception {
        mockMvc.perform(post("/register")
                        .param("firstName", "Jane")
                        .param("lastName", "Doe")
                        .param("email", "jane@example.test")
                        .param("phone", "1234567890")
                        .param("password", "Passw0rd!")
                        .param("confirmPassword", "SomethingElse!")
                        .param("address", "1 Test Street"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registerRequest", "confirmPassword"));

        verify(customerService, never()).register(any());
    }

    @Test
    void register_whenEmailAlreadyExists_reRendersFormWithErrorMessage_notA500() throws Exception {
        when(customerService.register(any()))
                .thenThrow(new DuplicateEmailException("An account with this email already exists"));

        mockMvc.perform(post("/register")
                        .param("firstName", "Jane")
                        .param("lastName", "Doe")
                        .param("email", "jane@example.test")
                        .param("phone", "1234567890")
                        .param("password", "Passw0rd!")
                        .param("confirmPassword", "Passw0rd!")
                        .param("address", "1 Test Street"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("errorMessage", "An account with this email already exists"));
    }

    @Test
    void register_whenPhoneAlreadyExists_reRendersFormWithErrorMessage_notA500() throws Exception {
        when(customerService.register(any()))
                .thenThrow(new DuplicatePhoneException("An account with this phone number already exists"));

        mockMvc.perform(post("/register")
                        .param("firstName", "Jane")
                        .param("lastName", "Doe")
                        .param("email", "jane@example.test")
                        .param("phone", "1234567890")
                        .param("password", "Passw0rd!")
                        .param("confirmPassword", "Passw0rd!")
                        .param("address", "1 Test Street"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("errorMessage", "An account with this phone number already exists"));
    }

    @Test
    void register_withValidData_redirectsToLoginWithSuccessFlash() throws Exception {
        Customer saved = TestDataFactory.customer();
        when(customerService.register(any())).thenReturn(saved);

        mockMvc.perform(post("/register")
                        .param("firstName", "Jane")
                        .param("lastName", "Doe")
                        .param("email", "jane@example.test")
                        .param("phone", "1234567890")
                        .param("password", "Passw0rd!")
                        .param("confirmPassword", "Passw0rd!")
                        .param("address", "1 Test Street"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    // ---------------------------------------------------------------- login

    @Test
    void showLogin_rendersFormWithEmptyCommandObject() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("loginRequest"));
    }

    @Test
    void login_withWrongCredentials_showsErrorMessage_notARedirect() throws Exception {
        when(customerService.login(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/login")
                        .param("email", "jane@example.test")
                        .param("password", "wrong-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attribute("errorMessage", "Invalid email or password"));
    }

    @Test
    void login_withValidCredentials_redirectsHomeWithSuccessFlash() throws Exception {
        Customer customer = TestDataFactory.customer("Jane", "Doe", "Passw0rd!", "1 Test Street");
        when(customerService.login(anyString(), anyString())).thenReturn(customer);

        mockMvc.perform(post("/login")
                        .param("email", customer.getEmail())
                        .param("password", "Passw0rd!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage", "Welcome back, Jane!"));
    }

    @Test
    void logout_redirectsToLoginWithSuccessFlash() throws Exception {
        mockMvc.perform(get("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("successMessage"));
    }
}
