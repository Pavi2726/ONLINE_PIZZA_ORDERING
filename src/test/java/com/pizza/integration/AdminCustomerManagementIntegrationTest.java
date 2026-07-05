package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Customer;
import com.pizza.repository.CustomerRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end coverage of admin customer management (US-015, US-016): real
 * {@code MockMvc} calls through the real {@link com.pizza.controller.AdminCustomerController},
 * real {@link com.pizza.service.AdminCustomerService} and the real
 * {@link CustomerRepository} backed by H2.
 */
class AdminCustomerManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    // -------------------------------------------------------------------- list

    @Test
    void list_reflectsRealSeededCustomers() throws Exception {
        Customer customer1 = customerRepository.saveAndFlush(TestDataFactory.customer());
        Customer customer2 = customerRepository.saveAndFlush(
                TestDataFactory.customer("Second", "Person", "Passw0rd!", "2 Other Street"));

        mockMvc.perform(get("/admin/customers").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-customer-list"))
                .andExpect(model().attribute("customers", hasItems(
                        hasProperty("email", is(customer1.getEmail())),
                        hasProperty("email", is(customer2.getEmail())))));
    }

    // -------------------------------------------------------------------- edit

    @Test
    void showEditForm_prefillsRealPersistedCustomerValues() throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());

        mockMvc.perform(get("/admin/customers/edit/" + customer.getId()).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("edit-customer"))
                .andExpect(model().attribute("customerId", customer.getId()))
                .andExpect(model().attribute("customerDTO", allOf(
                        hasProperty("firstName", is(customer.getFirstName())),
                        hasProperty("lastName", is(customer.getLastName())),
                        hasProperty("email", is(customer.getEmail())),
                        hasProperty("phone", is(customer.getPhone())),
                        hasProperty("address", is(customer.getAddress())))));
    }

    @Test
    void updateCustomer_withValidNonCollidingData_persistsRealChangesInH2() throws Exception {
        Customer customer = customerRepository.saveAndFlush(TestDataFactory.customer());
        Long id = customer.getId();
        Customer freshValues = TestDataFactory.customer();

        mockMvc.perform(post("/admin/customers/update/" + id)
                        .param("firstName", "Updated")
                        .param("lastName", "Name")
                        .param("email", freshValues.getEmail())
                        .param("phone", freshValues.getPhone())
                        .param("address", "99 Updated Avenue")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers"))
                .andExpect(flash().attribute("successMessage",
                        "Customer \"Updated Name\" updated successfully."));

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
    void updateCustomer_withDuplicateEmail_isRejectedWithFlashError_andRealRowIsUntouched() throws Exception {
        Customer other = customerRepository.saveAndFlush(TestDataFactory.customer());
        Customer target = customerRepository.saveAndFlush(TestDataFactory.customer());
        Long targetId = target.getId();
        String originalFirstName = target.getFirstName();
        String originalEmail = target.getEmail();
        String originalPhone = target.getPhone();
        String originalAddress = target.getAddress();

        mockMvc.perform(post("/admin/customers/update/" + targetId)
                        .param("firstName", "Should Not")
                        .param("lastName", "Apply")
                        .param("email", other.getEmail())
                        .param("phone", target.getPhone())
                        .param("address", "Should not be saved")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers"))
                .andExpect(flash().attribute("errorMessage",
                        "Another account with this email already exists"));

        // The real, H2-persisted row for target is provably untouched - the
        // service throws DuplicateEmailException before mutating/saving the
        // managed entity, and AdminCustomerController.updateCustomer's
        // try/catch swallows it into a flash error rather than a 500.
        Customer reloaded = customerRepository.findById(targetId).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo(originalFirstName);
        assertThat(reloaded.getEmail()).isEqualTo(originalEmail);
        assertThat(reloaded.getPhone()).isEqualTo(originalPhone);
        assertThat(reloaded.getAddress()).isEqualTo(originalAddress);
    }
}
