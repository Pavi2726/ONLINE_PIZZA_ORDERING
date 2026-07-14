package com.pizza.api;

import com.pizza.api.dto.ApiResponses.AdminResponse;
import com.pizza.api.dto.ApiResponses.CartItemResponse;
import com.pizza.api.dto.ApiResponses.CouponResponse;
import com.pizza.api.dto.ApiResponses.CustomerResponse;
import com.pizza.api.dto.ApiResponses.DrinkResponse;
import com.pizza.api.dto.ApiResponses.OrderItemResponse;
import com.pizza.api.dto.ApiResponses.OrderResponse;
import com.pizza.api.dto.ApiResponses.PizzaResponse;
import com.pizza.entity.Admin;
import com.pizza.entity.CartItem;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.entity.Drink;
import com.pizza.entity.Order;
import com.pizza.entity.OrderItem;
import com.pizza.entity.OrderStatus;
import com.pizza.entity.Pizza;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Entity → response-record mapping. The only place entities are read for the wire. */
public final class ApiMappers {

    /** Matches the window enforced by {@code OrderService} and the old edit-order page. */
    private static final long EDIT_WINDOW_MINUTES = 5;

    private static final String PLACED = "PLACED";

    private ApiMappers() {
    }

    public static PizzaResponse pizza(Pizza p) {
        if (p == null) {
            return null;
        }
        return new PizzaResponse(p.getId(), p.getName(), p.getDescription(), p.getCategory(),
                p.getPrice(), p.getImageUrl(), p.isAvailable());
    }

    public static List<PizzaResponse> pizzas(List<Pizza> list) {
        return list.stream().map(ApiMappers::pizza).toList();
    }

    public static DrinkResponse drink(Drink d) {
        if (d == null) {
            return null;
        }
        return new DrinkResponse(d.getId(), d.getName(), d.getDescription(), d.getCategory(),
                d.getPrice(), d.getImageUrl(), d.getSize(), d.isAvailable());
    }

    public static List<DrinkResponse> drinks(List<Drink> list) {
        return list.stream().map(ApiMappers::drink).toList();
    }

    public static CouponResponse coupon(Coupon c) {
        if (c == null) {
            return null;
        }
        return new CouponResponse(c.getId(), c.getCouponCode(), c.getDiscountPercentage(),
                c.isActive(), c.getCreatedAt());
    }

    public static List<CouponResponse> coupons(List<Coupon> list) {
        return list.stream().map(ApiMappers::coupon).toList();
    }

    public static CustomerResponse customer(Customer c) {
        if (c == null) {
            return null;
        }
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(),
                c.getFirstName() + " " + c.getLastName(),
                c.getEmail(), c.getPhone(), c.getAddress(), c.getCreatedAt());
    }

    public static List<CustomerResponse> customers(List<Customer> list) {
        return list.stream().map(ApiMappers::customer).toList();
    }

    public static AdminResponse admin(Admin a) {
        if (a == null) {
            return null;
        }
        return new AdminResponse(a.getId(), a.getName(), a.getEmail());
    }

    public static CartItemResponse cartItem(CartItem item) {
        Pizza pizza = item.getPizza();
        Drink drink = item.getDrink();
        BigDecimal unitPrice = item.getUnitPrice();
        if (pizza != null) {
            return new CartItemResponse(
                    item.getId(), "PIZZA",
                    pizza.getId(), pizza.getName(), pizza.getImageUrl(),
                    null, null, null, null, null,
                    unitPrice, item.getQuantity(),
                    unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        } else {
            return new CartItemResponse(
                    item.getId(), "DRINK",
                    null, null, null,
                    drink.getId(), drink.getName(), drink.getImageUrl(),
                    drink.getCategory(), drink.getSize(),
                    unitPrice, item.getQuantity(),
                    unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }
    }

    public static OrderItemResponse orderItem(OrderItem item) {
        Pizza pizza = item.getPizza();
        Drink drink = item.getDrink();
        if (pizza != null) {
            return new OrderItemResponse(
                    item.getId(), "PIZZA",
                    pizza.getId(), pizza.getName(), pizza.getImageUrl(),
                    null, null, null,
                    item.getPrice(), item.getQuantity(), item.getLineTotal());
        } else if (drink != null) {
            return new OrderItemResponse(
                    item.getId(), "DRINK",
                    null, null, null,
                    drink.getId(), drink.getName(), drink.getImageUrl(),
                    item.getPrice(), item.getQuantity(), item.getLineTotal());
        } else {
            // Fallback: deleted product
            return new OrderItemResponse(
                    item.getId(), "UNKNOWN",
                    null, null, null,
                    null, null, null,
                    item.getPrice(), item.getQuantity(), item.getLineTotal());
        }
    }

    /**
     * Maps an order for the customer's own views — {@code customer} is left null
     * so a LAZY association is never touched. Use {@link #orderForAdmin(Order)}
     * where the customer block is actually rendered.
     */
    public static OrderResponse order(Order o) {
        return order(o, null);
    }

    /** Admin views render the customer block; the admin finders JOIN FETCH it. */
    public static OrderResponse orderForAdmin(Order o) {
        return order(o, customer(o.getCustomer()));
    }

    private static OrderResponse order(Order o, CustomerResponse customer) {
        if (o == null) {
            return null;
        }
        String status = o.getStatus();
        return new OrderResponse(
                o.getId(),
                o.getOrderNumber(),
                status,
                OrderStatus.stepIndex(status),
                estimatedWindow(status),
                allowedNextStatuses(status),
                o.getOrderTime(),
                o.getCreatedAt(),
                o.getUpdatedAt(),
                o.getSubtotal(),
                o.getDiscountAmount(),
                o.getDiscountPercentage(),
                o.getCouponCode(),
                o.getTax(),
                o.getTotalAmount(),
                o.getDeliveryAddress(),
                o.getPhone(),
                o.getOrderItems().stream().map(ApiMappers::orderItem).toList(),
                customer,
                PLACED.equals(status),
                withinEditWindow(o));
    }

    public static List<OrderResponse> orders(List<Order> list) {
        return list.stream().map(ApiMappers::order).toList();
    }

    public static List<OrderResponse> ordersForAdmin(List<Order> list) {
        return list.stream().map(ApiMappers::orderForAdmin).toList();
    }

    /** True while the order is still inside the five-minute edit window. */
    public static boolean withinEditWindow(Order o) {
        if (!PLACED.equals(o.getStatus()) || o.getOrderTime() == null) {
            return false;
        }
        return Duration.between(o.getOrderTime(), LocalDateTime.now()).toMinutes() < EDIT_WINDOW_MINUTES;
    }

    private static String estimatedWindow(String status) {
        OrderStatus parsed = parse(status);
        return parsed == null ? "" : parsed.estimatedWindowLabel();
    }

    /**
     * The transitions the admin detail page may offer. Derived from the enum so the
     * rules live in exactly one place.
     */
    private static List<String> allowedNextStatuses(String status) {
        OrderStatus parsed = parse(status);
        if (parsed == null) {
            return List.of();
        }
        return Set.of(OrderStatus.values()).stream()
                .map(Enum::name)
                .filter(parsed::canTransitionTo)
                .sorted()
                .toList();
    }

    /** Status is a free-text column; unrecognized values must not blow up a read. */
    private static OrderStatus parse(String status) {
        if (status == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
