package com.pizza;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pizza.entity.Admin;
import com.pizza.entity.Customer;
import com.pizza.service.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for all Spring-context-backed tests.
 *
 * <p>Boots the full application context against the in-memory H2 database
 * (see {@code application-test.properties}) instead of the live Aiven MySQL
 * instance, mocks out {@link CloudinaryService} so no real network call to
 * Cloudinary is ever made, and wraps each test method in a transaction that
 * is rolled back afterwards so tests never leak state into one another.
 *
 * <p>The application is a JSON API behind a React SPA, so the helpers below drive
 * the real {@code /api/**} endpoints. Authentication is still {@code HttpSession} +
 * JSESSIONID, so a {@link MockHttpSession} obtained from a login response is a real,
 * usable session exactly as before.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Passw0rd!";

    @MockBean
    protected CloudinaryService cloudinaryService;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Serializes a body to JSON for a request. */
    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Registers a customer through the real API and returns a session logged in as them. */
    protected MockHttpSession registerAndLogin(Customer template) throws Exception {
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
                .andExpect(status().isCreated());

        return loginCustomer(template.getEmail());
    }

    /** Logs an already-registered customer in and returns their real session. */
    protected MockHttpSession loginCustomer(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Logs an already-persisted admin in and returns their real session. */
    protected MockHttpSession loginAdmin(Admin admin, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", admin.getEmail(), "password", rawPassword))))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
