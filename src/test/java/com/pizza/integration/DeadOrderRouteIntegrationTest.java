package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Customer;
import com.pizza.entity.Pizza;
import com.pizza.repository.PizzaRepository;
import com.pizza.testsupport.TestDataFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack version of {@code OrderControllerTest}'s (Task 5,
 * {@code @WebMvcTest} slice) dead-route characterization: {@code
 * OrderController.showOrderForm} renders {@code place-order.html}, whose
 * form does {@code th:field="*{pizzaId}"} and {@code th:field="*{quantity}"}
 * against the {@code orderDTO} model attribute - but {@link
 * com.pizza.dto.OrderDTO} only declares {@code deliveryAddress}, {@code
 * phone} and {@code couponCode}; it has no {@code pizzaId}/{@code quantity}
 * properties at all.
 *
 * <p>This class re-runs that exact scenario against the full {@code
 * @SpringBootTest} context: a real, auto-configured (non-mocked) Thymeleaf
 * {@code TemplateEngine}, a real H2-backed {@link Pizza} row, and a real
 * customer session established through the real {@code /register}/{@code
 * /login} endpoints - to confirm whether the same "the rendering exception
 * escapes {@code MockMvc.perform()} entirely, rather than being caught by
 * {@code GlobalExceptionHandler}" mechanism observed in the slice test still
 * holds here, or whether something in the fuller context changes it.
 *
 * <p>It does not differ, and reading {@code DispatcherServlet}'s source
 * explains why: {@code @WebMvcTest} vs {@code @SpringBootTest} only changes
 * which beans are loaded into the context, not the servlet dispatch
 * mechanism itself. {@code DispatcherServlet.doDispatch} only routes
 * exceptions thrown by <i>handler invocation</i> (the try/catch around
 * {@code ha.handle(...)}) through {@code processHandlerException}, which is
 * what lets {@code @ExceptionHandler} methods in {@code
 * GlobalExceptionHandler} run at all. View rendering ({@code
 * TemplateEngine.process}) happens later, in {@code
 * DispatcherServlet.render}, invoked from {@code processDispatchResult} -
 * outside that try/catch, regardless of which Spring Boot test slice
 * assembled the {@code DispatcherServlet}. A rendering exception therefore
 * still propagates straight out of {@code doDispatch}/{@code doService}/
 * {@code processRequest} and out of {@code MockMvc.perform(...)} itself, even
 * in this full {@code @SpringBootTest(webEnvironment = MOCK)} context - there
 * is no real embedded servlet container here for a container-level error
 * page mechanism to intercept it either. Confirmed by running this exact
 * test and inspecting the resulting stack trace, exactly as the slice test's
 * Javadoc describes.
 */
class DeadOrderRouteIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PizzaRepository pizzaRepository;

    private MockHttpSession registerAndLogin(Customer template) throws Exception {
        mockMvc.perform(post("/register")
                        .param("firstName", template.getFirstName())
                        .param("lastName", template.getLastName())
                        .param("email", template.getEmail())
                        .param("phone", template.getPhone())
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD)
                        .param("address", template.getAddress()))
                .andExpect(status().is3xxRedirection());

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("email", template.getEmail())
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    @Test
    void showOrderForm_withRealSessionAndRealAvailablePizza_throwsDuringViewRendering_escapesPerformEntirely() throws Exception {
        Customer template = TestDataFactory.customer();
        MockHttpSession session = registerAndLogin(template);
        assertThat(session).isNotNull();

        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());

        // No .andExpect(status()...) here: per the class Javadoc, the
        // exception happens during real Thymeleaf view rendering, which sits
        // outside the phase that produces any HTTP response at all - it
        // propagates directly out of perform() itself, even full-stack.
        Exception thrown = Assertions.assertThrows(Exception.class, () ->
                mockMvc.perform(get("/orders/new")
                        .param("pizzaId", String.valueOf(pizza.getId()))
                        .session(session)));

        Throwable root = thrown;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(NotReadablePropertyException.class);
        assertThat(root.getMessage()).contains("pizzaId").contains("OrderDTO");
    }
}
