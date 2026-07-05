package com.pizza;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Throwaway smoke test proving the Task 1 test infrastructure actually
 * works: the Spring context boots against H2 (not the live Aiven MySQL),
 * {@link com.pizza.service.CloudinaryService} is mocked out, and MockMvc can
 * dispatch a request through the full stack. Every later test in this suite
 * depends on this wiring being correct.
 */
class SmokeTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPizzasReturnsOk() throws Exception {
        mockMvc.perform(get("/pizzas")).andExpect(status().isOk());
    }
}
