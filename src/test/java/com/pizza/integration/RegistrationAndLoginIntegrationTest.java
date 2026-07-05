package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Customer;
import com.pizza.repository.CustomerRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end coverage of registration, login and logout (US-001/US-002):
 * real {@code MockMvc} calls hit the real {@link com.pizza.controller.AuthController},
 * real {@link com.pizza.service.CustomerService} and real {@link CustomerRepository}
 * backed by H2 - no mocking beyond the inherited {@code CloudinaryService} stub
 * (which these flows never touch).
 */
class RegistrationAndLoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "Passw0rd!";

    private void registerViaHttp(Customer template) throws Exception {
        mockMvc.perform(post("/register")
                        .param("firstName", template.getFirstName())
                        .param("lastName", template.getLastName())
                        .param("email", template.getEmail())
                        .param("phone", template.getPhone())
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD)
                        .param("address", template.getAddress()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void register_withValidData_persistsCustomerRowWithHashedPasswordInH2() throws Exception {
        Customer template = TestDataFactory.customer();

        registerViaHttp(template);

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

    @Test
    void register_withDuplicateEmail_isRejectedAndOnlyOneRowPersists() throws Exception {
        Customer template = TestDataFactory.customer();
        registerViaHttp(template);

        Customer secondAttempt = TestDataFactory.customer();
        mockMvc.perform(post("/register")
                        .param("firstName", "Someone")
                        .param("lastName", "Else")
                        .param("email", template.getEmail())
                        .param("phone", secondAttempt.getPhone())
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD)
                        .param("address", "A different address"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("errorMessage",
                        "An account with this email already exists"));

        long matchingRows = customerRepository.findAll().stream()
                .filter(c -> c.getEmail().equals(template.getEmail()))
                .count();
        assertThat(matchingRows).isEqualTo(1);
    }

    @Test
    void login_establishesUsableSession_andLogoutClearsIt() throws Exception {
        Customer template = TestDataFactory.customer();
        registerViaHttp(template);

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("email", template.getEmail())
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("successMessage"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(SessionUtil.CURRENT_CUSTOMER)).isNotNull();

        // The same session is usable for a subsequent authenticated request.
        mockMvc.perform(get("/orders/history").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("order-history"));

        // /logout clears the customer principal from that session.
        mockMvc.perform(get("/logout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(session.getAttribute(SessionUtil.CURRENT_CUSTOMER)).isNull();

        // The now-anonymous session can no longer reach a customer-only route.
        mockMvc.perform(get("/orders/history").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
