package com.pizza.service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pizza.dto.OrderDTO;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.CouponRepository;
import com.pizza.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business logic for placing orders (US-007): total calculation, order-number
 * generation and persistence. New orders default to status {@code PLACED}.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    /** Tax rate applied to the subtotal (8%). */
    private static final BigDecimal TAX_RATE = new BigDecimal("0.08");
    private static final String DEFAULT_STATUS = "PLACED";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final PizzaService pizzaService;

    private final CouponRepository couponRepository;



    /**
     * Places an order for the given customer.
     *
     * @param dto      the validated order form
     * @param customer the logged-in customer
     * @return the persisted order with totals and order number populated
     */
    @Transactional
    public Order placeOrder(OrderDTO dto, Customer customer) {
        // Defense in depth: never trust the client. Quantity is re-validated
        // and the price is always read from the database, so totals can only be
        // computed server-side and cannot be manipulated.
        if (dto.getQuantity() == null || dto.getQuantity() < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        Pizza pizza = pizzaService.findById(dto.getPizzaId());
        if (!pizza.isAvailable()) {
            throw new ResourceNotFoundException("This pizza is currently unavailable");
        }

        BigDecimal quantity = BigDecimal.valueOf(dto.getQuantity());

BigDecimal subtotal = pizza.getPrice()
        .multiply(quantity)
        .setScale(2, RoundingMode.HALF_UP);

// Default discount = 0
BigDecimal discount = BigDecimal.ZERO;
String couponCode = null;
Integer discountPercentage = null;

// Apply coupon if entered
if (dto.getCouponCode() != null && !dto.getCouponCode().trim().isEmpty()) {

    Coupon coupon = couponRepository
            .findByCouponCode(dto.getCouponCode().trim().toUpperCase())
            .orElseThrow(() ->
                    new IllegalArgumentException("Invalid coupon code."));

    if (!coupon.isActive()) {
        throw new IllegalArgumentException("Coupon is inactive.");
    }

couponCode = coupon.getCouponCode();
discountPercentage = coupon.getDiscountPercentage();

discount = subtotal
        .multiply(BigDecimal.valueOf(discountPercentage))
        .divide(BigDecimal.valueOf(100))
        .setScale(2, RoundingMode.HALF_UP);
        
}

// Subtotal after discount
BigDecimal discountedSubtotal = subtotal.subtract(discount);

BigDecimal tax = discountedSubtotal
        .multiply(TAX_RATE)
        .setScale(2, RoundingMode.HALF_UP);

BigDecimal total = discountedSubtotal
        .add(tax)
        .setScale(2, RoundingMode.HALF_UP);

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customer(customer)
                .pizza(pizza)
                .quantity(dto.getQuantity())
                .subtotal(discountedSubtotal)
                .tax(tax)
                .couponCode(couponCode)
                .discountPercentage(discountPercentage)
                .discountAmount(discount)
                .totalAmount(total)
                .deliveryAddress(dto.getDeliveryAddress().trim())
                .phone(dto.getPhone().trim())
                .status(DEFAULT_STATUS)
                .build();

        return orderRepository.saveAndFlush(order);
    }

    /** Finds an order by its public order number. */
    @Transactional(readOnly = true)
    public Order findByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderNumber));
    }

    /**
     * Finds an order only when it belongs to the given customer. Prevents one
     * customer from viewing another customer's confirmation page.
     */
    @Transactional(readOnly = true)
    public Order findByOrderNumberForCustomer(String orderNumber, Long customerId) {
        return orderRepository.findByOrderNumberAndCustomerId(orderNumber, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderNumber));
    }
    @Transactional(readOnly = true)
    public List<Order> getOrderHistory(Long customerId) {
        return orderRepository.findAllByCustomerId(customerId);
   }

   @Transactional(readOnly = true)
    public Order getOrderForEdit(Long orderId, Long customerId) {

    Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order not found"));

    if (!DEFAULT_STATUS.equals(order.getStatus())) {
        throw new IllegalStateException("Only placed orders can be updated.");
    }

    return order;
    }
    @Transactional
    public Order updateOrder(Long orderId,
                         OrderDTO dto,
                         Long customerId) {

    Order order = getOrderForEdit(orderId, customerId);

    if (dto.getQuantity() == null || dto.getQuantity() < 1) {
        throw new IllegalArgumentException("Quantity must be at least 1");
    }

    BigDecimal quantity = BigDecimal.valueOf(dto.getQuantity());

    BigDecimal subtotal = order.getPizza().getPrice()
        .multiply(quantity)
        .setScale(2, RoundingMode.HALF_UP);

// Recalculate discount if coupon was used
BigDecimal discount = BigDecimal.ZERO;

if (order.getDiscountPercentage() != null) {

    discount = subtotal
            .multiply(BigDecimal.valueOf(order.getDiscountPercentage()))
            .divide(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
}

BigDecimal discountedSubtotal = subtotal.subtract(discount);

BigDecimal tax = discountedSubtotal
        .multiply(TAX_RATE)
        .setScale(2, RoundingMode.HALF_UP);

BigDecimal total = discountedSubtotal
        .add(tax)
        .setScale(2, RoundingMode.HALF_UP);

    order.setQuantity(dto.getQuantity());
    order.setDeliveryAddress(dto.getDeliveryAddress().trim());
    order.setPhone(dto.getPhone().trim());

    order.setSubtotal(discountedSubtotal);
    order.setDiscountAmount(discount);
    order.setTax(tax);
    order.setTotalAmount(total);

    return orderRepository.save(order);
   }

   @Transactional
public void cancelOrder(Long orderId, Long customerId) {

    Order order = getOrderForEdit(orderId, customerId);

    order.setStatus("CANCELLED");

    orderRepository.save(order);
}
    /**
     * Generates a unique, human-friendly order number such as
     * {@code ORD-20260625-4821}.
     */
    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DATE_FMT);
        String candidate;
        do {
            int random = ThreadLocalRandom.current().nextInt(1000, 10000);
            candidate = "ORD-" + datePart + "-" + random;
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }
}
