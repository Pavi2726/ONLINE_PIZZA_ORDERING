package com.pizza.testsupport;

import com.pizza.entity.Admin;
import com.pizza.entity.Cart;
import com.pizza.entity.CartItem;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.entity.Drink;
import com.pizza.entity.Order;
import com.pizza.entity.OrderItem;
import com.pizza.entity.Pizza;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds throwaway entity instances for tests.
 *
 * <p>None of these methods persist anything - callers pass the result to a
 * repository/service themselves. Every entity that has a unique DB column
 * (Customer/Admin email, Customer phone, Order orderNumber, Coupon
 * couponCode) has that column suffixed with a value from a single shared,
 * monotonically increasing counter, so calling the same factory method
 * repeatedly - across one test method or many test classes sharing the same
 * H2 instance - never collides on a unique constraint.
 */
public final class TestDataFactory {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestDataFactory() {
    }

    private static long next() {
        return SEQUENCE.incrementAndGet();
    }

    // ---------------------------------------------------------------- Customer

    public static Customer customer() {
        return customer("Jane", "Doe", "Passw0rd!", "1 Test Street, Testville");
    }

    public static Customer customer(String firstName, String lastName, String password, String address) {
        long n = next();
        return Customer.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email("customer" + n + "@example.test")
                .phone(uniquePhone(n))
                .password(password)
                .address(address)
                .build();
    }

    // ---------------------------------------------------------------- Admin

    public static Admin admin() {
        return admin("Test Admin", "Passw0rd!");
    }

    public static Admin admin(String name, String password) {
        long n = next();
        return Admin.builder()
                .name(name)
                .email("admin" + n + "@example.test")
                .password(password)
                .build();
    }

    // ---------------------------------------------------------------- Pizza

    public static Pizza pizza() {
        return pizza("Margherita", new BigDecimal("9.99"), "Classic", true);
    }

    public static Pizza pizza(String name, BigDecimal price, String category, boolean available) {
        long n = next();
        return Pizza.builder()
                .name(name)
                .description("Test pizza description #" + n)
                .category(category)
                .price(price)
                .imageUrl("https://example.test/pizza-" + n + ".jpg")
                .imagePublicId("pizza-ordering/pizzas/test-" + n)
                .available(available)
                .build();
    }

    // ---------------------------------------------------------------- Drink

    public static Drink drink() {
        return drink("Cola", new BigDecimal("2.99"), "Soft Drinks", true);
    }

    public static Drink drink(String name, BigDecimal price, String category, boolean available) {
        long n = next();
        return Drink.builder()
                .name(name)
                .description("Test drink description #" + n)
                .category(category)
                .price(price)
                .size("500ml")
                .imageUrl("https://example.test/drink-" + n + ".jpg")
                .imagePublicId("pizza-ordering/drinks/test-" + n)
                .available(available)
                .build();
    }

    // ---------------------------------------------------------------- Order

    /** An order placed "now", with sensible default amounts. */
    public static Order order(Customer customer) {
        return order(customer, LocalDateTime.now(), "PLACED");
    }

    /**
     * An order with an explicit {@code orderTime}/{@code status}, e.g. for
     * testing the order-edit time-window logic without sleeping in the test.
     */
    public static Order order(Customer customer, LocalDateTime orderTime, String status) {
        return order(customer, orderTime, status,
                new BigDecimal("20.00"), new BigDecimal("1.00"), new BigDecimal("21.00"));
    }

    public static Order order(Customer customer, LocalDateTime orderTime, String status,
            BigDecimal subtotal, BigDecimal tax, BigDecimal totalAmount) {
        long n = next();
        return Order.builder()
                .orderNumber("ORD-TEST-" + n)
                .customer(customer)
                .orderTime(orderTime)
                .subtotal(subtotal)
                .tax(tax)
                .totalAmount(totalAmount)
                .deliveryAddress(customer.getAddress())
                .phone(customer.getPhone())
                .status(status)
                .build();
    }

    // ---------------------------------------------------------------- OrderItem

    public static OrderItem orderItem(Order order, Pizza pizza, int quantity) {
        BigDecimal price = pizza.getPrice();
        return OrderItem.builder()
                .order(order)
                .pizza(pizza)
                .quantity(quantity)
                .price(price)
                .lineTotal(price.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    public static OrderItem orderItem(Order order, Drink drink, int quantity) {
        BigDecimal price = drink.getPrice();
        return OrderItem.builder()
                .order(order)
                .drink(drink)
                .quantity(quantity)
                .price(price)
                .lineTotal(price.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    // ---------------------------------------------------------------- Coupon

    public static Coupon coupon() {
        return coupon(10, true);
    }

    public static Coupon coupon(int discountPercentage, boolean active) {
        long n = next();
        return Coupon.builder()
                .couponCode("COUPON" + n)
                .discountPercentage(discountPercentage)
                .active(active)
                .build();
    }

    // ---------------------------------------------------------------- Cart / CartItem
    // Hand-written entities: no Lombok, no builder - plain constructor + setters.

    public static Cart cart(String username) {
        Cart cart = new Cart();
        cart.setUsername(username);
        return cart;
    }

    public static CartItem cartItem(Cart cart, Pizza pizza, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setPizza(pizza);
        item.setQuantity(quantity);
        return item;
    }

    public static CartItem cartItem(Cart cart, Drink drink, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setDrink(drink);
        item.setQuantity(quantity);
        return item;
    }

    private static String uniquePhone(long n) {
        // "555" + a zero-padded, always-unique suffix - stays well within
        // Customer.phone's 20-character column limit.
        String suffix = String.valueOf(n);
        String padded = "0".repeat(Math.max(0, 7 - suffix.length())) + suffix;
        return "555" + padded;
    }
}
