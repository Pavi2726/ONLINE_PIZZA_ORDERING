package com.pizza.service;


import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pizza.entity.Cart;
import com.pizza.entity.CartItem;
import com.pizza.entity.Pizza;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.CartItemRepository;
import com.pizza.repository.CartRepository;
import com.pizza.repository.PizzaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private static final int MAX_QUANTITY_PER_ITEM = 50;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PizzaRepository pizzaRepository;
    /**
     * Returns the user's cart if it exists.
     * Otherwise creates a new empty cart.
     */
    public Cart getOrCreateCart(String username) {

        return cartRepository.findByUsername(username)
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUsername(username);
                    return cartRepository.save(cart);
                });
    }
    public void addPizzaToCart(String username, Long pizzaId) {

    Cart cart = getOrCreateCart(username);

    Pizza pizza = pizzaRepository.findById(pizzaId)
            .orElseThrow(() -> new ResourceNotFoundException("Pizza not found"));

    CartItem cartItem = cartItemRepository
            .findByCartAndPizza(cart, pizza)
            .orElse(null);

    if (cartItem != null) {
        if (cartItem.getQuantity() >= MAX_QUANTITY_PER_ITEM) {
            throw new IllegalArgumentException("Maximum quantity per item is " + MAX_QUANTITY_PER_ITEM + ".");
        }
        cartItem.setQuantity(cartItem.getQuantity() + 1);
    } else {
        cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setPizza(pizza);
        cartItem.setQuantity(1);
    }

    cartItemRepository.save(cartItem);
}
@Transactional(readOnly = true)
public Cart getCart(String username) {

    return cartRepository.findByUsername(username)
            .orElseGet(() -> {
                Cart cart = new Cart();
                cart.setUsername(username);
                return cartRepository.save(cart);
            });
}
public void removeItem(Long cartItemId, String username) {

    CartItem item = cartItemRepository.findByIdAndCart_Username(cartItemId, username)
            .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

    Cart cart = item.getCart();

    cart.getCartItems().remove(item);

    cartRepository.save(cart);
}
public void increaseQuantity(Long cartItemId, String username) {

    CartItem item = cartItemRepository.findByIdAndCart_Username(cartItemId, username)
            .orElseThrow(() -> new ResourceNotFoundException("Cart Item not found"));

    if (item.getQuantity() >= MAX_QUANTITY_PER_ITEM) {
        throw new IllegalArgumentException("Maximum quantity per item is " + MAX_QUANTITY_PER_ITEM + ".");
    }

    item.setQuantity(item.getQuantity() + 1);

    cartItemRepository.save(item);
}
public void decreaseQuantity(Long cartItemId, String username) {

    CartItem item = cartItemRepository.findByIdAndCart_Username(cartItemId, username)
            .orElseThrow(() -> new ResourceNotFoundException("Cart Item not found"));

    if (item.getQuantity() > 1) {

        item.setQuantity(item.getQuantity() - 1);
        cartItemRepository.save(item);

    } else {

        cartItemRepository.delete(item);

    }
}

public BigDecimal getCartSubtotal(String email) {

    Cart cart = getCart(email);

    BigDecimal subtotal = BigDecimal.ZERO;

    for (CartItem item : cart.getCartItems()) {

        BigDecimal itemTotal = item.getPizza()
                .getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        subtotal = subtotal.add(itemTotal);
    }

    return subtotal;
}
@Transactional
public void clearCart(String username) {

    Cart cart = getCart(username);

    cart.getCartItems().clear();

    cartRepository.save(cart);
}

/**
 * Total quantity across all cart lines (not the number of distinct lines),
 * used for the navbar cart badge. Uses a dedicated aggregate query so it
 * doesn't load the full Cart.cartItems/CartItem.pizza EAGER graph just to
 * count.
 */
@Transactional(readOnly = true)
public int getItemCount(String username) {
    Integer sum = cartItemRepository.sumQuantityByCartUsername(username);
    return sum == null ? 0 : sum;
}
}