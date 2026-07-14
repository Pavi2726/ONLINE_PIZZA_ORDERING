package com.pizza.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pizza.dto.OrderDTO;
import com.pizza.entity.Cart;
import com.pizza.entity.CartItem;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.CouponRepository;
import com.pizza.repository.OrderRepository;
import com.pizza.testsupport.TestDataFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link OrderService} (US-007 and the
 * order-edit flow). Constructor dependencies, read directly off
 * {@code OrderService}'s {@code @RequiredArgsConstructor} fields:
 * {@link OrderRepository}, {@link CartService}, {@link PizzaService},
 * {@link CouponRepository}.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartService cartService;

    @Mock
    private PizzaService pizzaService;

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private OrderService orderService;

    private Customer customer() {
        Customer customer = TestDataFactory.customer();
        customer.setId(1L);
        return customer;
    }

    private OrderDTO orderDto(String couponCode) {
        OrderDTO dto = new OrderDTO();
        dto.setDeliveryAddress("42 Slice Street");
        dto.setPhone("5551234567");
        dto.setCouponCode(couponCode);
        return dto;
    }

    /** Builds a cart and re-parents each item onto it, so item.getCart() is consistent. */
    private Cart cartWith(CartItem... items) {
        Cart cart = TestDataFactory.cart("customer@example.test");
        for (CartItem item : items) {
            item.setCart(cart);
            cart.getCartItems().add(item);
        }
        return cart;
    }

    private CartItem cartItem(BigDecimal price, int quantity) {
        Pizza pizza = TestDataFactory.pizza("Test Pizza", price, "Classic", true);
        return TestDataFactory.cartItem(TestDataFactory.cart("placeholder"), pizza, quantity);
    }

    // ---------------------------------------------------------------- placeOrder: totals

    @Test
    void placeOrder_noCoupon_computesSubtotalTaxAndTotalOnFullSubtotal() {
        Customer customer = customer();
        // Two items: 10.00 x1 + 2.50 x2 = 15.00 subtotal.
        Cart cart = cartWith(cartItem(new BigDecimal("10.00"), 1), cartItem(new BigDecimal("2.50"), 2));
        when(cartService.getCart(customer.getEmail())).thenReturn(cart);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.placeOrder(orderDto(null), customer);

        // subtotal 15.00, no discount, tax = 15.00 * 0.08 = 1.20, total = 16.20
        assertThat(result.getSubtotal()).isEqualByComparingTo("15.00");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(result.getTax()).isEqualByComparingTo("1.20");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("16.20");
        assertThat(result.getStatus()).isEqualTo("PLACED");
        assertThat(result.getCouponCode()).isNull();
        verify(cartService).clearCart(customer.getEmail());
    }

    @Test
    void placeOrder_withActiveCoupon_appliesDiscountBeforeTax() {
        Customer customer = customer();
        Cart cart = cartWith(cartItem(new BigDecimal("10.00"), 1), cartItem(new BigDecimal("2.50"), 2));
        when(cartService.getCart(customer.getEmail())).thenReturn(cart);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Coupon coupon = TestDataFactory.coupon(10, true);
        coupon.setCouponCode("SAVE10");
        when(couponRepository.findByCouponCode("SAVE10")).thenReturn(Optional.of(coupon));

        Order result = orderService.placeOrder(orderDto("save10"), customer);

        // subtotal 15.00, discount 10% = 1.50, discounted subtotal 13.50,
        // tax = 13.50 * 0.08 = 1.08 (computed on the POST-discount subtotal),
        // total = 13.50 + 1.08 = 14.58.
        assertThat(result.getSubtotal()).isEqualByComparingTo("15.00");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("1.50");
        assertThat(result.getTax()).isEqualByComparingTo("1.08");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("14.58");
        assertThat(result.getCouponCode()).isEqualTo("SAVE10");
        assertThat(result.getDiscountPercentage()).isEqualTo(10);
    }

    // ---------------------------------------------------------------- placeOrder: failures

    @Test
    void placeOrder_emptyCart_throwsIllegalStateException() {
        Customer customer = customer();
        when(cartService.getCart(customer.getEmail())).thenReturn(cartWith());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.placeOrder(orderDto(null), customer));

        assertThat(ex.getMessage()).isEqualTo("Your cart is empty.");
        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(anyString());
    }

    @Test
    void placeOrder_unknownCouponCode_throwsIllegalArgumentException() {
        Customer customer = customer();
        Cart cart = cartWith(cartItem(new BigDecimal("10.00"), 1));
        when(cartService.getCart(customer.getEmail())).thenReturn(cart);
        when(couponRepository.findByCouponCode("BOGUS")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(orderDto("bogus"), customer));

        assertThat(ex.getMessage()).isEqualTo("Invalid coupon code.");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_inactiveCoupon_throwsIllegalArgumentException() {
        Customer customer = customer();
        Cart cart = cartWith(cartItem(new BigDecimal("10.00"), 1));
        when(cartService.getCart(customer.getEmail())).thenReturn(cart);

        Coupon inactive = TestDataFactory.coupon(10, false);
        inactive.setCouponCode("STALE10");
        when(couponRepository.findByCouponCode("STALE10")).thenReturn(Optional.of(inactive));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(orderDto("stale10"), customer));

        assertThat(ex.getMessage()).isEqualTo("Coupon is inactive.");
        verify(orderRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- cancelOrder

    @Test
    void cancelOrder_whenPlaced_cancelsSuccessfully() {
        Customer customer = customer();
        Order order = TestDataFactory.order(customer, LocalDateTime.now(), "PLACED");
        order.setId(10L);
        when(orderRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(10L, 1L);

        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrder_whenNotPlaced_throwsIllegalStateExceptionAndDoesNotSave() {
        Customer customer = customer();
        Order order = TestDataFactory.order(customer, LocalDateTime.now(), "PROCESSING");
        order.setId(11L);
        when(orderRepository.findByIdAndCustomerId(11L, 1L)).thenReturn(Optional.of(order));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.cancelOrder(11L, 1L));

        assertThat(ex.getMessage()).isEqualTo("Only placed orders can be cancelled.");
        assertThat(order.getStatus()).isEqualTo("PROCESSING");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_orderNotFound_throwsResourceNotFoundException() {
        when(orderRepository.findByIdAndCustomerId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.cancelOrder(999L, 1L));
        verify(orderRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- 5-minute edit window
    // (updateOrderDetails is the simplest public method that goes through
    // validateEditWindow, so it is used to exercise that guard directly.)

    @Test
    void updateOrderDetails_withinFiveMinuteWindow_succeeds() {
        Customer customer = customer();
        Order order = TestDataFactory.order(customer, LocalDateTime.now().minusMinutes(1), "PLACED");
        order.setId(20L);
        when(orderRepository.findByIdAndCustomerId(20L, 1L)).thenReturn(Optional.of(order));

        orderService.updateOrderDetails(20L, "99 New Ave", "5559876543", 1L);

        assertThat(order.getDeliveryAddress()).isEqualTo("99 New Ave");
        assertThat(order.getPhone()).isEqualTo("5559876543");
        verify(orderRepository).save(order);
    }

    @Test
    void updateOrderDetails_outsideFiveMinuteWindow_throwsIllegalStateExceptionAndDoesNotSave() {
        Customer customer = customer();
        Order order = TestDataFactory.order(customer, LocalDateTime.now().minusMinutes(10), "PLACED");
        order.setId(21L);
        String originalAddress = order.getDeliveryAddress();
        when(orderRepository.findByIdAndCustomerId(21L, 1L)).thenReturn(Optional.of(order));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.updateOrderDetails(21L, "99 New Ave", "5559876543", 1L));

        assertThat(ex.getMessage())
                .isEqualTo("The 5-minute update window has expired. Please reorder to make any changes.");
        assertThat(order.getDeliveryAddress()).isEqualTo(originalAddress);
        verify(orderRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- reorder (Task 7)

    @Test
    void reorder_allItemsAvailableAndUnderCap_appendsEachViaClampedAddAndReturnsCounts() {
        Customer customer = customer();
        Order order = TestDataFactory.order(customer, LocalDateTime.now(), "DELIVERED");
        order.setId(30L);
        Pizza pizzaA = TestDataFactory.pizza("Margherita", new BigDecimal("9.99"), "Classic", true);
        pizzaA.setId(1L);
        Pizza pizzaB = TestDataFactory.pizza("Pepperoni", new BigDecimal("11.99"), "Classic", true);
        pizzaB.setId(2L);
        order.addOrderItem(TestDataFactory.orderItem(order, pizzaA, 2));
        order.addOrderItem(TestDataFactory.orderItem(order, pizzaB, 1));

        when(orderRepository.findByIdAndCustomerId(30L, 1L)).thenReturn(Optional.of(order));
        when(cartService.addPizzaToCartClamped(customer.getEmail(), 1L, 2)).thenReturn(false);
        when(cartService.addPizzaToCartClamped(customer.getEmail(), 2L, 1)).thenReturn(false);

        OrderService.ReorderResult result = orderService.reorder(30L, 1L);

        assertThat(result.addedCount()).isEqualTo(2);
        assertThat(result.skippedNames()).isEmpty();
        assertThat(result.cappedNames()).isEmpty();
        verify(cartService).addPizzaToCartClamped(customer.getEmail(), 1L, 2);
        verify(cartService).addPizzaToCartClamped(customer.getEmail(), 2L, 1);
    }

    @Test
    void reorder_oneItemUnavailable_isSkippedAndNeverSentToCartService() {
        Customer customer = customer();
        Order order = TestDataFactory.order(customer, LocalDateTime.now(), "DELIVERED");
        order.setId(31L);
        Pizza available = TestDataFactory.pizza("Margherita", new BigDecimal("9.99"), "Classic", true);
        available.setId(1L);
        Pizza unavailable = TestDataFactory.pizza("Retired Special", new BigDecimal("11.99"), "Classic", false);
        unavailable.setId(2L);
        order.addOrderItem(TestDataFactory.orderItem(order, available, 2));
        order.addOrderItem(TestDataFactory.orderItem(order, unavailable, 1));

        when(orderRepository.findByIdAndCustomerId(31L, 1L)).thenReturn(Optional.of(order));
        when(cartService.addPizzaToCartClamped(customer.getEmail(), 1L, 2)).thenReturn(false);

        OrderService.ReorderResult result = orderService.reorder(31L, 1L);

        assertThat(result.addedCount()).isEqualTo(1);
        assertThat(result.skippedNames()).containsExactly("Retired Special");
        assertThat(result.cappedNames()).isEmpty();
        verify(cartService, never()).addPizzaToCartClamped(anyString(), eq(2L), anyInt());
    }

    @Test
    void reorder_itemThatWouldExceedCap_isReflectedInCappedPizzaNamesAndStillCountsAsAdded() {
        Customer customer = customer();
        Order order = TestDataFactory.order(customer, LocalDateTime.now(), "DELIVERED");
        order.setId(32L);
        Pizza pizza = TestDataFactory.pizza("Margherita", new BigDecimal("9.99"), "Classic", true);
        pizza.setId(1L);
        order.addOrderItem(TestDataFactory.orderItem(order, pizza, 40));

        when(orderRepository.findByIdAndCustomerId(32L, 1L)).thenReturn(Optional.of(order));
        when(cartService.addPizzaToCartClamped(customer.getEmail(), 1L, 40)).thenReturn(true);

        OrderService.ReorderResult result = orderService.reorder(32L, 1L);

        assertThat(result.addedCount()).isEqualTo(1);
        assertThat(result.skippedNames()).isEmpty();
        assertThat(result.cappedNames()).containsExactly("Margherita");
    }

    @Test
    void reorder_orderNotOwnedByCustomer_throwsResourceNotFoundExceptionAndNeverTouchesCart() {
        when(orderRepository.findByIdAndCustomerId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.reorder(999L, 1L));

        verifyNoInteractions(cartService);
    }
}
