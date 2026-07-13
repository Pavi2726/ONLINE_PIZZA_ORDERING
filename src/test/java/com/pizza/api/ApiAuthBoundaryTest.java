package com.pizza.api;

import com.pizza.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The single most important behaviour the SPA migration changed.
 *
 * <p>The auth interceptors used to answer an unauthenticated request with a 302 to an
 * HTML login page. {@code fetch} follows a redirect transparently and hands back that
 * page with a 200, so an XHR could never distinguish "not logged in" from "succeeded".
 * Guarded API routes must therefore answer 401 with a JSON body, which the client turns
 * into a redirect of its own.
 */
class ApiAuthBoundaryTest extends AbstractIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/cart", "/api/orders"})
    void customerRoutes_withNoSession_answer401Json_notARedirect(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Please log in to continue."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/dashboard", "/api/admin/pizzas", "/api/admin/orders",
            "/api/admin/coupons", "/api/admin/customers"})
    void adminRoutes_withNoSession_answer401Json_notARedirect(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Admin login required."));
    }

    /** The bootstrap probe is deliberately unguarded - logged out is a normal answer. */
    @Test
    void meAndPublicCatalogue_areReachableWithoutASession() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pizzas")).andExpect(status().isOk());
    }

    /** An unknown API path must still 404 as JSON, not fall through to the SPA's index.html. */
    @Test
    void unknownApiPath_404sRatherThanServingTheSpaShell() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
