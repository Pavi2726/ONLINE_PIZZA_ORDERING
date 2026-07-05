package com.pizza.controller;

import com.pizza.entity.Admin;
import com.pizza.exception.InvalidCredentialsException;
import com.pizza.service.AdminService;
import com.pizza.service.PizzaService;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link AdminController}. {@code WebMvcConfig}
 * registers {@code AdminAuthInterceptor} against {@code /admin/**}, excluding
 * only {@code /admin/login} and {@code /admin/logout}; that interceptor is a
 * {@code @Component} {@code HandlerInterceptor} so it loads automatically in
 * this slice and is what actually produces the redirect-to-login behavior
 * asserted below for the protected routes.
 */
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private PizzaService pizzaService;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    // ------------------------------------------------------------- bare /admin

    @Test
    void bareAdminPath_withNoSession_redirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void bareAdminPath_withAdminSession_redirectsToDashboard() throws Exception {
        mockMvc.perform(get("/admin").session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    // ------------------------------------------------------------------- login

    @Test
    void showLogin_withNoSession_rendersLoginForm() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-login"))
                .andExpect(model().attributeExists("loginRequest"));
    }

    @Test
    void showLogin_whenAlreadyLoggedIn_redirectsToDashboard() throws Exception {
        mockMvc.perform(get("/admin/login").session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void login_withWrongCredentials_showsErrorMessage_notARedirect() throws Exception {
        when(adminService.login(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/admin/login")
                        .param("email", "admin@example.test")
                        .param("password", "wrong-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-login"))
                .andExpect(model().attribute("errorMessage", "Invalid email or password"));
    }

    @Test
    void login_withValidCredentials_redirectsToDashboardWithSuccessFlash() throws Exception {
        Admin admin = TestDataFactory.admin("Test Admin", "Passw0rd!");
        when(adminService.login(anyString(), anyString())).thenReturn(admin);

        mockMvc.perform(post("/admin/login")
                        .param("email", admin.getEmail())
                        .param("password", "Passw0rd!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andExpect(flash().attribute("successMessage", "Welcome, Test Admin!"));
    }

    // --------------------------------------------------------------- dashboard

    @Test
    void dashboard_withNoSession_redirectsToAdminLogin_andNeverCallsPizzaService() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verifyNoInteractions(pizzaService);
    }

    @Test
    void dashboard_withAdminSession_rendersStatsFromPizzaService() throws Exception {
        when(pizzaService.countAll()).thenReturn(10L);
        when(pizzaService.countAvailable()).thenReturn(7L);
        when(pizzaService.countOutOfStock()).thenReturn(3L);

        mockMvc.perform(get("/admin/dashboard").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dashboard"))
                .andExpect(model().attribute("totalPizzas", 10L))
                .andExpect(model().attribute("availablePizzas", 7L))
                .andExpect(model().attribute("outOfStockPizzas", 3L));
    }

    // ------------------------------------------------------------------ logout

    @Test
    void logout_redirectsToAdminLoginWithSuccessFlash() throws Exception {
        mockMvc.perform(get("/admin/logout").session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void logout_withNoSession_stillRedirectsToAdminLogin() throws Exception {
        // /admin/logout is explicitly excluded from AdminAuthInterceptor in
        // WebMvcConfig, so this reaches the controller directly rather than
        // being intercepted; the controller's own logic still lands on the
        // same redirect target.
        mockMvc.perform(get("/admin/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verify(adminService, never()).login(anyString(), anyString());
    }
}
