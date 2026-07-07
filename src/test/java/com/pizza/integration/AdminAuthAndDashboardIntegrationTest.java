package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Admin;
import com.pizza.entity.Customer;
import com.pizza.entity.Pizza;
import com.pizza.repository.AdminRepository;
import com.pizza.repository.CustomerRepository;
import com.pizza.repository.PizzaRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.math.BigDecimal;
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
 * End-to-end coverage of admin authentication and the dashboard overview:
 * real {@code MockMvc} calls through the real {@link com.pizza.controller.AdminController},
 * real {@link com.pizza.service.AdminService}/{@link com.pizza.service.PizzaService}
 * and the real {@link AdminRepository}/{@link PizzaRepository} backed by H2.
 */
class AdminAuthAndDashboardIntegrationTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

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
    void login_withValidCredentials_establishesAdminSessionAndRedirectsToDashboard() throws Exception {
        Admin admin = seedAdmin();

        MvcResult result = mockMvc.perform(post("/admin/login")
                        .param("email", admin.getEmail())
                        .param("password", RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andExpect(flash().attribute("successMessage", "Welcome, " + admin.getName() + "!"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        Object sessionAdmin = session.getAttribute(SessionUtil.CURRENT_ADMIN);
        assertThat(sessionAdmin).isInstanceOf(Admin.class);
        assertThat(((Admin) sessionAdmin).getEmail()).isEqualTo(admin.getEmail());

        // The session is genuinely usable for a subsequent protected admin request.
        mockMvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dashboard"));
    }

    @Test
    void login_withWrongPassword_isRejectedWithoutEstablishingSession() throws Exception {
        Admin admin = seedAdmin();

        mockMvc.perform(post("/admin/login")
                        .param("email", admin.getEmail())
                        .param("password", "WrongPassword!"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-login"))
                .andExpect(model().attribute("errorMessage", "Invalid email or password"));

        // A protected admin route is still unreachable - no session was ever created.
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
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

        pizzaRepository.saveAndFlush(TestDataFactory.pizza("Available One", new BigDecimal("9.00"), "Classic", true));
        pizzaRepository.saveAndFlush(TestDataFactory.pizza("Available Two", new BigDecimal("11.00"), "Classic", true));
        pizzaRepository.saveAndFlush(TestDataFactory.pizza("Out Of Stock", new BigDecimal("13.00"), "Classic", false));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, admin);

        mockMvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dashboard"))
                .andExpect(model().attribute("totalPizzas", baselineTotal + 3L))
                .andExpect(model().attribute("availablePizzas", baselineAvailable + 2L))
                .andExpect(model().attribute("outOfStockPizzas", baselineOutOfStock + 1L));
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
        mockMvc.perform(get("/admin/dashboard").session(adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/orders/history").session(customerSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/logout").session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("successMessage", "You have been logged out."));

        // The admin principal is gone from its own session...
        assertThat(adminSession.getAttribute(SessionUtil.CURRENT_ADMIN)).isNull();
        mockMvc.perform(get("/admin/dashboard").session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        // ...but the completely separate customer session is untouched and
        // still fully authenticated.
        assertThat(customerSession.getAttribute(SessionUtil.CURRENT_CUSTOMER)).isNotNull();
        mockMvc.perform(get("/orders/history").session(customerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("order-history"));
    }
}
