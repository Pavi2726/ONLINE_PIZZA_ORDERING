package com.pizza.service;

import com.pizza.entity.Cart;
import com.pizza.entity.CartItem;
import com.pizza.entity.Pizza;
import com.pizza.repository.CartItemRepository;
import com.pizza.repository.CartRepository;
import com.pizza.repository.PizzaRepository;
import com.pizza.testsupport.TestDataFactory;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link CartService}.
 *
 * <p>Includes characterization tests for a known, confirmed authorization
 * gap: {@code removeItem}/{@code increaseQuantity}/{@code decreaseQuantity}
 * take only a raw {@code cartItemId} - there is no customer/username
 * parameter anywhere in their signature or body for the service (or a unit
 * test) to check ownership against. These tests document that gap; they do
 * not add ownership checks, which is out of scope for this task.</p>
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private PizzaRepository pizzaRepository;

    @InjectMocks
    private CartService cartService;

    // ---------------------------------------------------------------- addPizzaToCart

    @Test
    void addPizzaToCart_incrementsQuantity_insteadOfDuplicatingRow_whenPizzaAlreadyInCart() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        pizza.setId(5L);
        CartItem existingItem = TestDataFactory.cartItem(cart, pizza, 2);
        cart.getCartItems().add(existingItem);

        when(cartRepository.findByUsername("jane@example.com")).thenReturn(Optional.of(cart));
        when(pizzaRepository.findById(5L)).thenReturn(Optional.of(pizza));
        when(cartItemRepository.findByCartAndPizza(cart, pizza)).thenReturn(Optional.of(existingItem));

        cartService.addPizzaToCart("jane@example.com", 5L);

        assertThat(existingItem.getQuantity()).isEqualTo(3);
        // The existing row is the one saved - no second CartItem is ever created.
        verify(cartItemRepository, times(1)).save(existingItem);
        verify(cartItemRepository, never()).save(argThatNotSameAs(existingItem));
    }

    @Test
    void addPizzaToCart_createsNewLine_whenPizzaNotYetInCart() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        pizza.setId(5L);

        when(cartRepository.findByUsername("jane@example.com")).thenReturn(Optional.of(cart));
        when(pizzaRepository.findById(5L)).thenReturn(Optional.of(pizza));
        when(cartItemRepository.findByCartAndPizza(cart, pizza)).thenReturn(Optional.empty());

        cartService.addPizzaToCart("jane@example.com", 5L);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        CartItem saved = captor.getValue();
        assertThat(saved.getQuantity()).isEqualTo(1);
        assertThat(saved.getCart()).isSameAs(cart);
        assertThat(saved.getPizza()).isSameAs(pizza);
    }

    private static CartItem argThatNotSameAs(CartItem other) {
        return org.mockito.ArgumentMatchers.argThat(candidate -> candidate != other);
    }

    // ---------------------------------------------------------------- increase / decrease

    @Test
    void decreaseQuantity_removesLine_whenQuantityReachesZero() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        CartItem item = TestDataFactory.cartItem(cart, pizza, 1);
        when(cartItemRepository.findById(11L)).thenReturn(Optional.of(item));

        cartService.decreaseQuantity(11L);

        verify(cartItemRepository).delete(item);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void decreaseQuantity_justDecrements_whenQuantityAboveOne() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        CartItem item = TestDataFactory.cartItem(cart, pizza, 3);
        when(cartItemRepository.findById(12L)).thenReturn(Optional.of(item));

        cartService.decreaseQuantity(12L);

        assertThat(item.getQuantity()).isEqualTo(2);
        verify(cartItemRepository).save(item);
        verify(cartItemRepository, never()).delete(any(CartItem.class));
    }

    // ---------------------------------------------------------------- characterization: missing ownership check

    @Test
    void mutationMethods_haveNoCustomerOrUsernameParameter() throws NoSuchMethodException {
        // These are the only mutators for a single cart line, and each takes
        // exactly one argument: the raw cartItemId. There is no
        // customer/username parameter for the method (or a caller) to check
        // ownership against. This reflects the current, confirmed gap in the
        // method signatures themselves - it is not fixed by this test.
        Method removeItem = CartService.class.getMethod("removeItem", Long.class);
        Method increaseQuantity = CartService.class.getMethod("increaseQuantity", Long.class);
        Method decreaseQuantity = CartService.class.getMethod("decreaseQuantity", Long.class);

        assertThat(removeItem.getParameterTypes()).containsExactly(Long.class);
        assertThat(increaseQuantity.getParameterTypes()).containsExactly(Long.class);
        assertThat(decreaseQuantity.getParameterTypes()).containsExactly(Long.class);
    }

    @Test
    void removeItem_succeedsForAnyCartItem_regardlessOfWhichCustomerOwnsIt() {
        // "otherCustomersCart" stands in for a cart that does not belong to
        // whichever caller invokes removeItem. Because removeItem(Long) has
        // no parameter carrying the caller's identity, there is nothing here
        // (or in the real method) to check that cartItemId 21 actually
        // belongs to the caller - it just acts on it.
        Cart otherCustomersCart = TestDataFactory.cart("victim@example.com");
        Pizza pizza = TestDataFactory.pizza();
        CartItem item = TestDataFactory.cartItem(otherCustomersCart, pizza, 1);
        otherCustomersCart.getCartItems().add(item);
        when(cartItemRepository.findById(21L)).thenReturn(Optional.of(item));

        cartService.removeItem(21L);

        assertThat(otherCustomersCart.getCartItems()).doesNotContain(item);
        verify(cartRepository).save(otherCustomersCart);
    }

    @Test
    void increaseQuantity_succeedsForAnyCartItem_regardlessOfWhichCustomerOwnsIt() {
        Cart otherCustomersCart = TestDataFactory.cart("victim@example.com");
        Pizza pizza = TestDataFactory.pizza();
        CartItem item = TestDataFactory.cartItem(otherCustomersCart, pizza, 1);
        when(cartItemRepository.findById(22L)).thenReturn(Optional.of(item));

        cartService.increaseQuantity(22L);

        assertThat(item.getQuantity()).isEqualTo(2);
        verify(cartItemRepository).save(item);
    }
}
