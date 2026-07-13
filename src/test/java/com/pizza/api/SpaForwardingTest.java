package com.pizza.api;

import com.pizza.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The SPA owns routes that exist only in the browser's router, so a hard refresh or a
 * pasted deep link must still be served the app shell. Three things have to stay true at
 * once, and the resource-resolver predicate is easy to get subtly wrong:
 * client routes fall back to index.html, the API keeps 404ing as JSON, and real static
 * assets are still served as themselves.
 *
 * <p>These run against the genuinely built {@code target/classes/static} output — the
 * frontend build is bound to process-resources, so it has already run by test time.</p>
 */
class SpaForwardingTest extends AbstractIntegrationTest {

    /**
     * These are all React Router routes with no server-side handler. They must come back
     * as the shell — in particular {@code /cart} and {@code /admin/orders/5} must NOT be
     * redirected to a login page by the auth interceptors, or the app could never boot
     * far enough to ask who the visitor is.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/pizzas", "/cart", "/orders/history", "/admin/orders/5", "/admin/login"})
    void clientSideRoutes_serveTheSpaShell(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<div id=\"root\">")))
                // Applied pre-paint, so a dark-mode visitor never sees a white flash.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pizza-theme")));
    }

    /**
     * "/" is claimed by Spring Boot's welcome-page mapping, which forwards to index.html
     * rather than writing it. MockMvc does not execute forwards, so the body reads empty
     * here even though a real container serves the shell — assert the forward target.
     */
    @Test
    void rootPath_forwardsToTheSpaShell() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .forwardedUrl("index.html"));
    }

    /** An unknown API path must 404, not silently hand back a page of HTML. */
    @Test
    void unknownApiPath_isNotSwallowedByTheFallback() throws Exception {
        mockMvc.perform(get("/api/definitely-not-a-route"))
                .andExpect(status().isNotFound());
    }

    /** A real asset must still be served as itself, not replaced by the shell. */
    @Test
    void staticAssets_areStillServedNormally() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<div id=\"root\">")));
    }
}
