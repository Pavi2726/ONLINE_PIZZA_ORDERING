package com.pizza.controller;

import com.pizza.entity.Customer;
import com.pizza.service.CartService;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test for {@link GlobalModelAdvice} - no Spring context.
 *
 * <p>Covers the {@code cartItemCount} model attribute that backs the navbar's
 * cart badge: it must return 0 without ever touching {@link CartService} when
 * there is no logged-in customer (so admin/login/register pages never trigger
 * a cart-count DB query), and must otherwise delegate to {@code
 * CartService.getItemCount} for the logged-in customer's email.</p>
 */
@ExtendWith(MockitoExtension.class)
class GlobalModelAdviceTest {

    @Mock
    private CartService cartService;

    @Test
    void cartItemCount_returnsZero_andNeverInvokesCartService_whenNoCustomerLoggedIn() {
        GlobalModelAdvice advice = new GlobalModelAdvice(cartService);
        MockHttpSession session = new MockHttpSession();

        Integer count = advice.cartItemCount(session);

        assertThat(count).isEqualTo(0);
        verifyNoInteractions(cartService);
    }

    @Test
    void cartItemCount_returnsCartServiceCount_forLoggedInCustomersEmail() {
        GlobalModelAdvice advice = new GlobalModelAdvice(cartService);
        Customer customer = TestDataFactory.customer();
        MockHttpSession session = new MockHttpSession();
        SessionUtil.loginCustomer(session, customer);
        when(cartService.getItemCount(customer.getEmail())).thenReturn(6);

        Integer count = advice.cartItemCount(session);

        assertThat(count).isEqualTo(6);
        verify(cartService).getItemCount(customer.getEmail());
    }
}
