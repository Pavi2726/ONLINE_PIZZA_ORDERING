package com.pizza.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response payloads for the JSON API.
 *
 * <p>Entities are never serialized directly: {@code Order.customer} is LAZY and
 * {@code open-in-view} is off, {@code Cart}/{@code Order} hold bidirectional
 * collections Jackson would recurse through, and {@code Customer.password}
 * carries the BCrypt hash. These records are the whole wire contract.</p>
 */
public final class ApiResponses {

    private ApiResponses() {
    }

    /**
     * Envelope for mutations. {@code messageType} is the direct successor of the
     * {@code successMessage}/{@code warningMessage}/{@code errorMessage} flash
     * attributes the Thymeleaf app redirected with, so the client can render the
     * same three alert styles.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Envelope<T>(String message, String messageType, T data) {

        public static <T> Envelope<T> success(String message, T data) {
            return new Envelope<>(message, "success", data);
        }

        public static <T> Envelope<T> warning(String message, T data) {
            return new Envelope<>(message, "warning", data);
        }
    }

    /** Error body for every non-2xx API response. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(int status, String error, String message, Map<String, String> fieldErrors) {

        public static ApiError of(int status, String error, String message) {
            return new ApiError(status, error, message, null);
        }
    }

    public record PizzaResponse(
            Long id,
            String name,
            String description,
            String category,
            BigDecimal price,
            String imageUrl,
            boolean available) {
    }

    public record PizzaListResponse(List<PizzaResponse> pizzas, List<String> categories) {
    }

    public record DrinkResponse(
            Long id,
            String name,
            String description,
            String category,
            BigDecimal price,
            String imageUrl,
            String size,
            boolean available) {
    }

    public record DrinkListResponse(List<DrinkResponse> drinks, List<String> categories) {
    }

    public record CouponResponse(
            Long id,
            String couponCode,
            Integer discountPercentage,
            boolean active,
            LocalDateTime createdAt) {
    }

    /**
     * Represents a single line in the cart. Either pizza or drink fields will be populated
     * (the other will be null). {@code itemType} is "PIZZA" or "DRINK".
     */
    public record CartItemResponse(
            Long id,
            String itemType,
            Long pizzaId,
            String pizzaName,
            String pizzaImageUrl,
            Long drinkId,
            String drinkName,
            String drinkImageUrl,
            String drinkCategory,
            String drinkSize,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal itemTotal) {
    }

    public record CartResponse(
            List<CartItemResponse> items,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal grandTotal,
            CouponResponse appliedCoupon,
            List<CouponResponse> activeCoupons,
            int itemCount) {
    }

    /**
     * Represents a single line in an order. Either pizza or drink fields will be populated.
     * {@code itemType} is "PIZZA" or "DRINK".
     */
    public record OrderItemResponse(
            Long id,
            String itemType,
            Long pizzaId,
            String pizzaName,
            String pizzaImageUrl,
            Long drinkId,
            String drinkName,
            String drinkImageUrl,
            BigDecimal price,
            Integer quantity,
            BigDecimal lineTotal) {
    }

    /**
     * {@code stepIndex}, {@code estimatedWindow} and {@code allowedNextStatuses}
     * come from the {@code OrderStatus} enum so the transition rules and the
     * status stepper are not duplicated in the browser. {@code cancellable} and
     * {@code editable} (the five-minute window) are likewise computed here — the
     * client's clock is not authoritative.
     */
    public record OrderResponse(
            Long id,
            String orderNumber,
            String status,
            int stepIndex,
            String estimatedWindow,
            List<String> allowedNextStatuses,
            LocalDateTime orderTime,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            Integer discountPercentage,
            String couponCode,
            BigDecimal tax,
            BigDecimal totalAmount,
            String deliveryAddress,
            String phone,
            List<OrderItemResponse> items,
            CustomerResponse customer,
            boolean cancellable,
            boolean editable) {
    }

    /** Never carries the password hash. */
    public record CustomerResponse(
            Long id,
            String firstName,
            String lastName,
            String fullName,
            String email,
            String phone,
            String address,
            LocalDateTime createdAt) {
    }

    public record AdminResponse(Long id, String name, String email) {
    }

    /** Successor to {@code GlobalModelAdvice} — the session bootstrap probe. */
    public record MeResponse(CustomerResponse customer, AdminResponse admin, int cartItemCount) {
    }

    public record DashboardStatsResponse(long totalPizzas, long availablePizzas, long outOfStockPizzas,
                                          long totalDrinks, long availableDrinks, long outOfStockDrinks) {
    }

    public record LoginResponse(CustomerResponse customer, int cartItemCount) {
    }
}
