package com.pizza.service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pizza.dto.EditOrderDTO;
import com.pizza.dto.EditOrderItemDTO;
import com.pizza.dto.OrderDTO;
import com.pizza.entity.Cart;
import com.pizza.entity.CartItem;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.OrderItem;
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
  private final CartService cartService;
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

    Cart cart = cartService.getCart(customer.getEmail());

    if (cart.getCartItems().isEmpty()) {
        throw new IllegalStateException("Your cart is empty.");
    }
    BigDecimal subtotal = BigDecimal.ZERO;

for (CartItem item : cart.getCartItems()) {
    subtotal = subtotal.add(item.getItemTotal());
}

BigDecimal discount = BigDecimal.ZERO;
String couponCode = null;
Integer discountPercentage = null;

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

BigDecimal discountedSubtotal = subtotal.subtract(discount);

BigDecimal tax = discountedSubtotal
        .multiply(TAX_RATE)
        .setScale(2, RoundingMode.HALF_UP);

BigDecimal total = discountedSubtotal
        .add(tax)
        .setScale(2, RoundingMode.HALF_UP);

    // We will continue here in the next step
    Order order = Order.builder()
        .orderNumber(generateOrderNumber())
        .customer(customer)
        .subtotal(subtotal)
        .discountAmount(discount)
        .discountPercentage(discountPercentage)
        .couponCode(couponCode)
        .tax(tax)
        .totalAmount(total)
        .deliveryAddress(dto.getDeliveryAddress().trim())
        .phone(dto.getPhone().trim())
        .status(DEFAULT_STATUS)
        .build();
        order.setOrderTime(LocalDateTime.now());
        for (CartItem cartItem : cart.getCartItems()) {

    OrderItem orderItem = OrderItem.builder()
            .pizza(cartItem.getPizza())
            .quantity(cartItem.getQuantity())
            .price(cartItem.getPizza().getPrice())
            .lineTotal(cartItem.getItemTotal())
            .build();

    order.addOrderItem(orderItem);
}
Order savedOrder = orderRepository.save(order);

cartService.clearCart(customer.getEmail());

return savedOrder;
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
public Order findOrderById(Long orderId, Long customerId) {

    return orderRepository
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order not found"));
}
    @Transactional(readOnly = true)
    public List<Order> getOrderHistory(Long customerId) {
        return orderRepository.findAllByCustomerId(customerId);
   }
private void validateEditWindow(Order order) {

    if (Duration.between(order.getOrderTime(), LocalDateTime.now()).toMinutes() >= 5) {
        throw new IllegalStateException(
                "The 5-minute update window has expired. Please reorder to make any changes.");
    }
}
   
   
  @Transactional
public void cancelOrder(Long orderId, Long customerId) {

    Order order = orderRepository
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order not found"));

    if (!DEFAULT_STATUS.equals(order.getStatus())) {
        throw new IllegalStateException("Only placed orders can be cancelled.");
    }

    order.setStatus("CANCELLED");

    orderRepository.save(order);
}
    /**
     * Generates a unique, human-friendly order number such as
     * {@code ORD-20260625-4821}.
     */
    
    private void recalculateOrderTotals(Order order) {

    BigDecimal subtotal = BigDecimal.ZERO;

    for (OrderItem item : order.getOrderItems()) {
        subtotal = subtotal.add(item.getLineTotal());
    }

    BigDecimal discount = BigDecimal.ZERO;

    if (order.getDiscountPercentage() != null) {
        discount = subtotal
                .multiply(BigDecimal.valueOf(order.getDiscountPercentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    BigDecimal discountedSubtotal = subtotal.subtract(discount);

    BigDecimal tax = discountedSubtotal
            .multiply(TAX_RATE)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal total = discountedSubtotal
            .add(tax)
            .setScale(2, RoundingMode.HALF_UP);

    order.setSubtotal(subtotal);
    order.setDiscountAmount(discount);
    order.setTax(tax);
    order.setTotalAmount(total);
}
    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DATE_FMT);
        String candidate;
        do {
            int random = ThreadLocalRandom.current().nextInt(1000, 10000);
            candidate = "ORD-" + datePart + "-" + random;
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }
@Transactional
public void increaseItemQuantity(Long orderId,
                                 Long itemId,
                                 Long customerId) {

    Order order = orderRepository
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order not found"));
validateEditWindow(order);
    OrderItem orderItem = order.getOrderItems()
            .stream()
            .filter(item -> item.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order item not found"));

    // Increase quantity
    orderItem.setQuantity(orderItem.getQuantity() + 1);

    // Update line total
    orderItem.setLineTotal(
            orderItem.getPrice()
                    .multiply(BigDecimal.valueOf(orderItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP)
    );

    // Recalculate order totals
    recalculateOrderTotals(order);

    // Save changes
    orderRepository.save(order);
}@Transactional
public void decreaseItemQuantity(Long orderId,
                                 Long itemId,
                                 Long customerId) {

    Order order = orderRepository
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order not found"));
validateEditWindow(order);
    OrderItem orderItem = order.getOrderItems()
            .stream()
            .filter(item -> item.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order item not found"));

    // Do not decrease below 1
    if (orderItem.getQuantity() <= 1) {
        return;
    }

    // Decrease quantity
    orderItem.setQuantity(orderItem.getQuantity() - 1);

    // Update line total
    orderItem.setLineTotal(
            orderItem.getPrice()
                    .multiply(BigDecimal.valueOf(orderItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP)
    );

    // Recalculate order totals
    recalculateOrderTotals(order);

    // Save changes
    orderRepository.save(order);
}
@Transactional
public void removeOrderItem(Long orderId,
                            Long itemId,
                            Long customerId) {

    Order order = orderRepository
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order not found"));
validateEditWindow(order);
    // Prevent removing the last pizza
    if (order.getOrderItems().size() <= 1) {
        return;
    }

    OrderItem orderItem = order.getOrderItems()
            .stream()
            .filter(item -> item.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order item not found"));

    // Remove the item
    order.removeOrderItem(orderItem);

    // Recalculate order totals
    recalculateOrderTotals(order);

    // Save changes
    orderRepository.save(order);
}
public EditOrderDTO getEditOrder(Long orderId, Long customerId) {

    Order order = findOrderById(orderId, customerId);

    EditOrderDTO dto = new EditOrderDTO();

    dto.setOrderId(order.getId());
    dto.setDeliveryAddress(order.getDeliveryAddress());
    dto.setPhone(order.getPhone());
    dto.setCouponCode(order.getCouponCode());

    List<EditOrderItemDTO> items = new ArrayList<>();

    for (OrderItem item : order.getOrderItems()) {

        EditOrderItemDTO itemDTO = new EditOrderItemDTO();

        itemDTO.setOrderItemId(item.getId());
        itemDTO.setPizzaId(item.getPizza().getId());
        itemDTO.setPizzaName(item.getPizza().getName());
        itemDTO.setQuantity(item.getQuantity());
        itemDTO.setPrice(item.getPrice());

        items.add(itemDTO);
    }

    dto.setItems(items);

    return dto;
}

@Transactional
public void addPizzaToOrder(Long orderId,
                            Long pizzaId,
                            Integer quantity,
                            Long customerId) {

    Order order = findOrderById(orderId, customerId);
    validateEditWindow(order);
                            
    Pizza pizza = pizzaService.findById(pizzaId);
                                if (!pizza.isAvailable()) {
    throw new IllegalStateException("Pizza is currently unavailable.");
}
    OrderItem existingItem = null;

    for (OrderItem item : order.getOrderItems()) {

        if (item.getPizza().getId().equals(pizzaId)) {
            existingItem = item;
            break;
        }
    }

    if (existingItem != null) {

        existingItem.setQuantity(
                existingItem.getQuantity() + quantity);

        existingItem.setLineTotal(
                existingItem.getPrice()
                        .multiply(BigDecimal.valueOf(existingItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
    }
    else {

        OrderItem newItem = new OrderItem();

        newItem.setOrder(order);
        newItem.setPizza(pizza);
        newItem.setQuantity(quantity);
        newItem.setPrice(pizza.getPrice());

        newItem.setLineTotal(
                pizza.getPrice()
                        .multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP));

        order.getOrderItems().add(newItem);
    }

    recalculateOrderTotals(order);

    orderRepository.save(order);
}
@Transactional
public void updateOrderDetails(Long orderId,
                               String deliveryAddress,
                               String phone,
                               Long customerId) {

    Order order = orderRepository
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() ->
                    new ResourceNotFoundException("Order not found"));
validateEditWindow(order);
    order.setDeliveryAddress(deliveryAddress.trim());
    order.setPhone(phone.trim());

    orderRepository.save(order);
}
}