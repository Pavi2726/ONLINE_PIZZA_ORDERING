package com.pizza.service;

import com.pizza.entity.Cart;
import com.pizza.entity.CartItem;
import com.pizza.entity.Pizza;
import com.pizza.exception.ResourceNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link CartService}.
 *
 * <p>Bug #2 fix: {@code removeItem}/{@code increaseQuantity}/{@code
 * decreaseQuantity} now take a {@code username} parameter and scope the
 * lookup via {@code CartItemRepository.findByIdAndCart_Username(...)}, so a
 * {@code cartItemId} that doesn't belong to the caller throws {@link
 * ResourceNotFoundException} instead of being silently mutated.</p>
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
        when(cartItemRepository.findByIdAndCart_Username(11L, "jane@example.com")).thenReturn(Optional.of(item));

        cartService.decreaseQuantity(11L, "jane@example.com");

        verify(cartItemRepository).delete(item);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void decreaseQuantity_justDecrements_whenQuantityAboveOne() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        CartItem item = TestDataFactory.cartItem(cart, pizza, 3);
        when(cartItemRepository.findByIdAndCart_Username(12L, "jane@example.com")).thenReturn(Optional.of(item));

        cartService.decreaseQuantity(12L, "jane@example.com");

        assertThat(item.getQuantity()).isEqualTo(2);
        verify(cartItemRepository).save(item);
        verify(cartItemRepository, never()).delete(any(CartItem.class));
    }

    // ---------------------------------------------------------------- Bug #2: ownership scoping

    @Test
    void mutationMethods_haveUsernameParameter_forOwnershipScoping() throws NoSuchMethodException {
        // Fixed signatures: each mutator now also takes the caller's
        // username, which is threaded down into a Cart-scoped repository
        // lookup so an id belonging to a different customer's cart can be
        // rejected instead of silently acted on.
        Method removeItem = CartService.class.getMethod("removeItem", Long.class, String.class);
        Method increaseQuantity = CartService.class.getMethod("increaseQuantity", Long.class, String.class);
        Method decreaseQuantity = CartService.class.getMethod("decreaseQuantity", Long.class, String.class);

        assertThat(removeItem.getParameterTypes()).containsExactly(Long.class, String.class);
        assertThat(increaseQuantity.getParameterTypes()).containsExactly(Long.class, String.class);
        assertThat(decreaseQuantity.getParameterTypes()).containsExactly(Long.class, String.class);
    }

    @Test
    void removeItem_throwsResourceNotFoundException_whenCartItemBelongsToDifferentCustomer() {
        // "attacker@example.com" is not the username of the cart that owns
        // cartItemId 21 - the scoped repository lookup returns empty, so the
        // service must reject the request instead of acting on it.
        when(cartItemRepository.findByIdAndCart_Username(21L, "attacker@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> cartService.removeItem(21L, "attacker@example.com"));

        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void increaseQuantity_throwsResourceNotFoundException_whenCartItemBelongsToDifferentCustomer() {
        when(cartItemRepository.findByIdAndCart_Username(22L, "attacker@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> cartService.increaseQuantity(22L, "attacker@example.com"));

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void removeItem_succeedsForOwnCartItem() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        CartItem item = TestDataFactory.cartItem(cart, pizza, 1);
        cart.getCartItems().add(item);
        when(cartItemRepository.findByIdAndCart_Username(21L, "jane@example.com")).thenReturn(Optional.of(item));

        cartService.removeItem(21L, "jane@example.com");

        assertThat(cart.getCartItems()).doesNotContain(item);
        verify(cartRepository).save(cart);
    }

    @Test
    void increaseQuantity_succeedsForOwnCartItem() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        CartItem item = TestDataFactory.cartItem(cart, pizza, 1);
        when(cartItemRepository.findByIdAndCart_Username(22L, "jane@example.com")).thenReturn(Optional.of(item));

        cartService.increaseQuantity(22L, "jane@example.com");

        assertThat(item.getQuantity()).isEqualTo(2);
        verify(cartItemRepository).save(item);
    }

    // ---------------------------------------------------------------- Bug #8: server-side quantity cap

    @Test
    void addPizzaToCart_throwsIllegalArgumentException_whenIncrementWouldExceedMaxQuantity() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        pizza.setId(5L);
        CartItem existingItem = TestDataFactory.cartItem(cart, pizza, 50);

        when(cartRepository.findByUsername("jane@example.com")).thenReturn(Optional.of(cart));
        when(pizzaRepository.findById(5L)).thenReturn(Optional.of(pizza));
        when(cartItemRepository.findByCartAndPizza(cart, pizza)).thenReturn(Optional.of(existingItem));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> cartService.addPizzaToCart("jane@example.com", 5L));

        assertThat(ex.getMessage()).isEqualTo("Maximum quantity per item is 50.");
        assertThat(existingItem.getQuantity()).isEqualTo(50);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void increaseQuantity_throwsIllegalArgumentException_atCap() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        CartItem item = TestDataFactory.cartItem(cart, pizza, 50);
        when(cartItemRepository.findByIdAndCart_Username(30L, "jane@example.com")).thenReturn(Optional.of(item));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> cartService.increaseQuantity(30L, "jane@example.com"));

        assertThat(ex.getMessage()).isEqualTo("Maximum quantity per item is 50.");
        assertThat(item.getQuantity()).isEqualTo(50);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // ---------------------------------------------------------------- addPizzaToCartClamped (Task 7: reorder)
    // Separate, distinctly-named method used exclusively by OrderService.reorder.
    // Unlike addPizzaToCart(String, Long), this clamps at the cap instead of
    // throwing, so a historical order can always be reordered in full.

    @Test
    void addPizzaToCartClamped_newItem_underCap_addsRequestedQuantityAndReturnsFalse() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        pizza.setId(5L);

        when(cartRepository.findByUsername("jane@example.com")).thenReturn(Optional.of(cart));
        when(pizzaRepository.findById(5L)).thenReturn(Optional.of(pizza));
        when(cartItemRepository.findByCartAndPizza(cart, pizza)).thenReturn(Optional.empty());

        boolean clamped = cartService.addPizzaToCartClamped("jane@example.com", 5L, 3);

        assertThat(clamped).isFalse();
        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(3);
        assertThat(captor.getValue().getCart()).isSameAs(cart);
        assertThat(captor.getValue().getPizza()).isSameAs(pizza);
    }

    @Test
    void addPizzaToCartClamped_existingItem_sumExceedsCap_clampsAtFiftyAndReturnsTrue() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        pizza.setId(5L);
        CartItem existingItem = TestDataFactory.cartItem(cart, pizza, 45);

        when(cartRepository.findByUsername("jane@example.com")).thenReturn(Optional.of(cart));
        when(pizzaRepository.findById(5L)).thenReturn(Optional.of(pizza));
        when(cartItemRepository.findByCartAndPizza(cart, pizza)).thenReturn(Optional.of(existingItem));

        boolean clamped = cartService.addPizzaToCartClamped("jane@example.com", 5L, 10);

        assertThat(clamped).isTrue();
        assertThat(existingItem.getQuantity()).isEqualTo(50);
        verify(cartItemRepository).save(existingItem);
    }

    @Test
    void addPizzaToCartClamped_newItem_requestedQuantityExceedsCap_clampsAtFiftyAndReturnsTrue() {
        Cart cart = TestDataFactory.cart("jane@example.com");
        Pizza pizza = TestDataFactory.pizza();
        pizza.setId(5L);

        when(cartRepository.findByUsername("jane@example.com")).thenReturn(Optional.of(cart));
        when(pizzaRepository.findById(5L)).thenReturn(Optional.of(pizza));
        when(cartItemRepository.findByCartAndPizza(cart, pizza)).thenReturn(Optional.empty());

        boolean clamped = cartService.addPizzaToCartClamped("jane@example.com", 5L, 75);

        assertThat(clamped).isTrue();
        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(50);
    }

    // ---------------------------------------------------------------- getItemCount (navbar badge)

    @Test
    void getItemCount_returnsZero_whenCartHasNoItemsOrNoCartRowYet() {
        // COALESCE(SUM(...), 0) protects against a null aggregate, but the
        // service must not blow up with an NPE even if the repository itself
        // ever returned a bare null.
        when(cartItemRepository.sumQuantityByCartUsername("jane@example.com")).thenReturn(null);

        int count = cartService.getItemCount("jane@example.com");

        assertThat(count).isEqualTo(0);
    }

    @Test
    void getItemCount_returnsSummedQuantity_notLineCount_whenCartHasMultipleLines() {
        // Two distinct lines (2 + 4 units) must report 6, not 2.
        when(cartItemRepository.sumQuantityByCartUsername("jane@example.com")).thenReturn(6);

        int count = cartService.getItemCount("jane@example.com");

        assertThat(count).isEqualTo(6);
    }
}
