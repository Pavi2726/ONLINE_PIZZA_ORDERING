package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Customer;
import com.pizza.repository.CustomerRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the admin customer list and edit (US-016) against real H2 rows.
 *
 * <p>A duplicate email raises {@code DuplicateEmailException}, which the API maps to a
 * 409 carrying the real message.
 */
class AdminCustomerManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    @Test
    void list_reflectsRealSeededCustomers() throws Exception {
        Customer customer1 = customerRepository.saveAndFlush(TestDataFactory.customer());
        Customer customer2 = customerRepository.saveAndFlush(
                TestDataFactory.customer("Second", "Person", "Passw0rd!", "2 Other Street"));

        String body = mockMvc.perform(get("/api/admin/customers").session(adminSession()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains(customer1.getEmail());
        assertThat(body).contains(customer2.getEmail());
    }

    /** The customer list must never carry password hashes. */
    @Test
    void list_neverExposesPasswordHashes() throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());

        String body = mockMvc.perform(get("/api/admin/customers").session(adminSession()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain(customer.getPassword());
        assertThat(body).doesNotContain("password");
    }

    @Test
    void list_withSearchParam_filtersToMatchingCustomerOnly() throws Exception {
        Customer match = customerRepository.saveAndFlush(
                TestDataFactory.customer("Zelda", "Uniquename", "Passw0rd!", "3 Unique Ave"));
        customerRepository.saveAndFlush(TestDataFactory.customer());
        customerRepository.saveAndFlush(TestDataFactory.customer());

        mockMvc.perform(get("/api/admin/customers").param("search", "Zelda").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(match.getEmail()));
    }

    @Test
    void getCustomer_returnsRealPersistedValues() throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());

        mockMvc.perform(get("/api/admin/customers/{id}", customer.getId()).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value(customer.getFirstName()))
                .andExpect(jsonPath("$.lastName").value(customer.getLastName()))
                .andExpect(jsonPath("$.email").value(customer.getEmail()))
                .andExpect(jsonPath("$.phone").value(customer.getPhone()))
                .andExpect(jsonPath("$.address").value(customer.getAddress()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void updateCustomer_withValidNonCollidingData_persistsRealChangesInH2() throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Long id = customer.getId();
        Customer freshValues = TestDataFactory.customer();

        mockMvc.perform(put("/api/admin/customers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Updated",
                                "lastName", "Name",
                                "email", freshValues.getEmail(),
                                "phone", freshValues.getPhone(),
                                "address", "99 Updated Avenue")))
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Customer \"Updated Name\" updated successfully."));

        entityManager.flush();
        entityManager.clear();

        Customer reloaded = customerRepository.findById(id).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Updated");
        assertThat(reloaded.getLastName()).isEqualTo("Name");
        assertThat(reloaded.getEmail()).isEqualTo(freshValues.getEmail());
        assertThat(reloaded.getPhone()).isEqualTo(freshValues.getPhone());
        assertThat(reloaded.getAddress()).isEqualTo("99 Updated Avenue");
    }

    @Test
    void updateCustomer_withDuplicateEmail_isRejected_andRealRowIsUntouched() throws Exception {
        Customer other = customerRepository.saveAndFlush(TestDataFactory.customer());
        Customer target = customerRepository.saveAndFlush(TestDataFactory.customer());
        Long targetId = target.getId();
        String originalFirstName = target.getFirstName();
        String originalEmail = target.getEmail();
        String originalPhone = target.getPhone();
        String originalAddress = target.getAddress();

        mockMvc.perform(put("/api/admin/customers/{id}", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Should Not",
                                "lastName", "Apply",
                                "email", other.getEmail(),
                                "phone", target.getPhone(),
                                "address", "Should not be saved")))
                        .session(adminSession()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Another account with this email already exists"));

        entityManager.flush();
        entityManager.clear();

        Customer reloaded = customerRepository.findById(targetId).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo(originalFirstName);
        assertThat(reloaded.getEmail()).isEqualTo(originalEmail);
        assertThat(reloaded.getPhone()).isEqualTo(originalPhone);
        assertThat(reloaded.getAddress()).isEqualTo(originalAddress);
    }
}
