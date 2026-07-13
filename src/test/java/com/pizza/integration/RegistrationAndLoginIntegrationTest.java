package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Customer;
import com.pizza.repository.CustomerRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of registration, login and logout (US-001/US-002): real
 * {@code MockMvc} calls hit the real {@code AuthApiController}, real
 * {@link com.pizza.service.CustomerService} and real {@link CustomerRepository}
 * backed by H2 - no mocking beyond the inherited {@code CloudinaryService} stub
 * (which these flows never touch).
 */
class RegistrationAndLoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void register_withValidData_persistsCustomerRowWithHashedPasswordInH2() throws Exception {
        Customer template = TestDataFactory.customer();

        registerAndLogin(template);

        Customer persisted = customerRepository.findByEmail(template.getEmail()).orElseThrow(
                () -> new AssertionError("Expected a persisted Customer row for " + template.getEmail()));
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getFirstName()).isEqualTo(template.getFirstName());
        assertThat(persisted.getLastName()).isEqualTo(template.getLastName());
        assertThat(persisted.getPhone()).isEqualTo(template.getPhone());
        // Real BCrypt hashing happened - the raw password is never stored.
        assertThat(persisted.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, persisted.getPassword())).isTrue();
    }

    /** The response must never carry the password hash, whatever else it carries. */
    @Test
    void register_neverEchoesThePasswordHashBackToTheClient() throws Exception {
        Customer template = TestDataFactory.customer();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", template.getFirstName(),
                                "lastName", template.getLastName(),
                                "email", template.getEmail(),
                                "phone", template.getPhone(),
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD,
                                "address", template.getAddress()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(template.getEmail()))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void register_withMismatchedPasswords_isRejectedWithAConfirmPasswordFieldError() throws Exception {
        Customer template = TestDataFactory.customer();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", template.getFirstName(),
                                "lastName", template.getLastName(),
                                "email", template.getEmail(),
                                "phone", template.getPhone(),
                                "password", PASSWORD,
                                "confirmPassword", "SomethingElse1!",
                                "address", template.getAddress()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.confirmPassword").value("Passwords do not match"));

        assertThat(customerRepository.findByEmail(template.getEmail())).isEmpty();
    }

    @Test
    void register_withDuplicateEmail_isRejectedAndOnlyOneRowPersists() throws Exception {
        Customer template = TestDataFactory.customer();
        registerAndLogin(template);

        Customer secondAttempt = TestDataFactory.customer();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Someone",
                                "lastName", "Else",
                                "email", template.getEmail(),
                                "phone", secondAttempt.getPhone(),
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD,
                                "address", "A different address"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));

        long matchingRows = customerRepository.findAll().stream()
                .filter(c -> c.getEmail().equals(template.getEmail()))
                .count();
        assertThat(matchingRows).isEqualTo(1);
    }

    @Test
    void login_withWrongPassword_isRejectedWithoutEstablishingSession() throws Exception {
        Customer template = TestDataFactory.customer();
        registerAndLogin(template);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", template.getEmail(), "password", "WrongPassword1!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_establishesUsableSession_andLogoutClearsIt() throws Exception {
        Customer template = TestDataFactory.customer();
        MockHttpSession session = registerAndLogin(template);

        assertThat(session).isNotNull();
        assertThat(session.getAttribute(SessionUtil.CURRENT_CUSTOMER)).isNotNull();

        // The same session is usable for a subsequent authenticated request.
        mockMvc.perform(get("/api/orders").session(session))
                .andExpect(status().isOk());

        // Logout clears the customer principal from that session.
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You have been logged out."));

        assertThat(session.getAttribute(SessionUtil.CURRENT_CUSTOMER)).isNull();

        // The now-anonymous session can no longer reach a customer-only route. It must be
        // a 401, not a redirect to an HTML login page — an XHR cannot act on a 302.
        mockMvc.perform(get("/api/orders").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please log in to continue."));
    }

    /** The session bootstrap probe must answer, not 401, when nobody is logged in. */
    @Test
    void me_withNoSession_returns200WithNullPrincipalsAndZeroCartCount() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.admin").doesNotExist())
                .andExpect(jsonPath("$.cartItemCount").value(0));
    }

    @Test
    void me_withCustomerSession_reportsTheLoggedInCustomer() throws Exception {
        Customer template = TestDataFactory.customer();
        MockHttpSession session = registerAndLogin(template);

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.email").value(template.getEmail()))
                .andExpect(jsonPath("$.customer.password").doesNotExist());
    }
}
