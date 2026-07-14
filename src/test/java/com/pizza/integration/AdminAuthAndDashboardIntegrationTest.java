package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Admin;
import com.pizza.entity.Customer;
import com.pizza.repository.AdminRepository;
import com.pizza.repository.CustomerRepository;
import com.pizza.repository.PizzaRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.math.BigDecimal;
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
 * End-to-end coverage of admin authentication and the dashboard overview: real
 * {@code MockMvc} calls through the real {@code AdminApiController}, real
 * {@link com.pizza.service.AdminService}/{@link com.pizza.service.PizzaService} and the
 * real {@link AdminRepository}/{@link PizzaRepository} backed by H2.
 */
class AdminAuthAndDashboardIntegrationTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "Passw0rd!";

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PizzaRepository pizzaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Seeds a real Admin row with a genuine BCrypt hash (no admin registration endpoint exists). */
    private Admin seedAdmin() {
        Admin admin = TestDataFactory.admin();
        admin.setPassword(passwordEncoder.encode(RAW_PASSWORD));
        return adminRepository.saveAndFlush(admin);
    }

    // ------------------------------------------------------------------- login

    @Test
    void login_withValidCredentials_establishesAdminSession() throws Exception {
        Admin admin = seedAdmin();

        MockHttpSession session = loginAdmin(admin, RAW_PASSWORD);

        assertThat(session).isNotNull();
        Object sessionAdmin = session.getAttribute(SessionUtil.CURRENT_ADMIN);
        assertThat(sessionAdmin).isInstanceOf(Admin.class);
        assertThat(((Admin) sessionAdmin).getEmail()).isEqualTo(admin.getEmail());

        // The session is genuinely usable for a subsequent protected admin request.
        mockMvc.perform(get("/api/admin/dashboard").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void login_withWrongPassword_isRejectedWithoutEstablishingSession() throws Exception {
        Admin admin = seedAdmin();

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", admin.getEmail(), "password", "WrongPassword!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        // A protected admin route is still unreachable - no session was ever created.
        // It answers 401, not a redirect: the SPA needs a status it can act on.
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Admin login required."));
    }

    /** A logged-in customer is not an admin, and must not reach an admin route. */
    @Test
    void customerSession_cannotReachAdminRoutes() throws Exception {
        Customer customer = TestDataFactory.customer();
        MockHttpSession customerSession = registerAndLogin(customer);

        mockMvc.perform(get("/api/admin/dashboard").session(customerSession))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- dashboard

    @Test
    void dashboard_reflectsRealH2SeededPizzaCounts() throws Exception {
        Admin admin = seedAdmin();

        // Baseline counts, taken from the real H2 table as it stands right now.
        // The pizzas table is shared across the suite (other tests may have left
        // committed rows behind), so the dashboard must be verified against a
        // known delta on top of whatever is already there rather than an
        // absolute hardcoded total - see PizzaService#countAll/countAvailable.
        long baselineTotal = pizzaRepository.count();
        long baselineAvailable = pizzaRepository.findByAvailableTrue().size();
        long baselineOutOfStock = baselineTotal - baselineAvailable;

        pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Available One", new BigDecimal("9.00"), "Classic", true));
        pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Available Two", new BigDecimal("11.00"), "Classic", true));
        pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Out Of Stock", new BigDecimal("13.00"), "Classic", false));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, admin);

        mockMvc.perform(get("/api/admin/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPizzas").value(baselineTotal + 3L))
                .andExpect(jsonPath("$.availablePizzas").value(baselineAvailable + 2L))
                .andExpect(jsonPath("$.outOfStockPizzas").value(baselineOutOfStock + 1L));
    }

    // ------------------------------------------------- session independence

    @Test
    void adminLogout_doesNotClearAnUnrelatedCustomerSession() throws Exception {
        // Two entirely separate MockHttpSessions - the same way a real admin
        // browser tab and a real customer browser tab would never share one.
        Admin admin = seedAdmin();
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute(SessionUtil.CURRENT_ADMIN, admin);

        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        MockHttpSession customerSession = new MockHttpSession();
        customerSession.setAttribute(SessionUtil.CURRENT_CUSTOMER, customer);

        // Sanity: both sessions are independently usable before logout.
        mockMvc.perform(get("/api/admin/dashboard").session(adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders").session(customerSession))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/logout").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You have been logged out."));

        // The admin principal is gone from its own session...
        assertThat(adminSession.getAttribute(SessionUtil.CURRENT_ADMIN)).isNull();
        mockMvc.perform(get("/api/admin/dashboard").session(adminSession))
                .andExpect(status().isUnauthorized());

        // ...but the completely separate customer session is untouched and
        // still fully authenticated.
        assertThat(customerSession.getAttribute(SessionUtil.CURRENT_CUSTOMER)).isNotNull();
        mockMvc.perform(get("/api/orders").session(customerSession))
                .andExpect(status().isOk());
    }
}
