package com.pizza.api;

import com.pizza.api.dto.ApiResponses.Envelope;
import com.pizza.api.dto.ApiResponses.OrderResponse;
import com.pizza.dto.OrderDTO;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.service.OrderService;
import com.pizza.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer order placement, history and in-window editing. Guarded by
 * {@code CustomerAuthInterceptor}.
 *
 * <p>The old edit-order flow stashed {@code editingAddress}/{@code editingPhone} in the
 * session so unsaved form input survived the redirect after each item change. The SPA
 * keeps that in component state, so those attributes are gone.</p>
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderService orderService;

    public record AddItemRequest(Long pizzaId, Integer quantity) {
    }

    public record UpdateDetailsRequest(String deliveryAddress, String phone) {
    }

    /** Places the cart as an order, using the customer's stored address and the session coupon. */
    @PostMapping
    public ResponseEntity<Envelope<OrderResponse>> place(HttpSession session) {
        Customer customer = customer(session);

        OrderDTO dto = new OrderDTO();
        dto.setDeliveryAddress(customer.getAddress());
        dto.setPhone(customer.getPhone());
        Coupon coupon = (Coupon) session.getAttribute(SessionKeys.APPLIED_COUPON);
        if (coupon != null) {
            dto.setCouponCode(coupon.getCouponCode());
        }

        try {
            Order order = orderService.placeOrder(dto, customer);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Envelope.success("Order placed successfully!", ApiMappers.order(order)));
        } finally {
            // Cleared whether or not placement succeeded, matching the old behaviour: a
            // coupon that failed validation at placement must not linger on the cart.
            session.removeAttribute(SessionKeys.APPLIED_COUPON);
        }
    }

    @GetMapping
    public List<OrderResponse> history(HttpSession session) {
        return ApiMappers.orders(orderService.getOrderHistory(customer(session).getId()));
    }

    @GetMapping("/by-number/{orderNumber}")
    public OrderResponse byNumber(@PathVariable String orderNumber, HttpSession session) {
        return ApiMappers.order(
                orderService.findByOrderNumberForCustomer(orderNumber, customer(session).getId()));
    }

    /** Backs the edit page; 409 once the five-minute window has closed. */
    @GetMapping("/{orderId}")
    public OrderResponse forEdit(@PathVariable Long orderId, HttpSession session) {
        Order order = orderService.findOrderById(orderId, customer(session).getId());
        if (!ApiMappers.withinEditWindow(order)) {
            throw new IllegalStateException(
                    "The edit time for this order has expired. Please place a new order to make changes.");
        }
        return ApiMappers.order(order);
    }

    @PostMapping("/{orderId}/cancel")
    public Envelope<Void> cancel(@PathVariable Long orderId, HttpSession session) {
        orderService.cancelOrder(orderId, customer(session).getId());
        return Envelope.success("Order cancelled successfully.", null);
    }

    /** Merges a past order's items back into the cart. */
    @PostMapping("/{orderId}/reorder")
    public Envelope<Void> reorder(@PathVariable Long orderId, HttpSession session) {
        OrderService.ReorderResult result = orderService.reorder(orderId, customer(session).getId());
        return Envelope.success(result.summaryMessage(), null);
    }

    @PutMapping("/{orderId}")
    public Envelope<OrderResponse> updateDetails(@PathVariable Long orderId,
                                                 @Valid @RequestBody UpdateDetailsRequest request,
                                                 HttpSession session) {
        Long customerId = customer(session).getId();
        orderService.updateOrderDetails(orderId, request.deliveryAddress(), request.phone(), customerId);
        return Envelope.success("Order updated successfully.", reload(orderId, customerId));
    }

    @PostMapping("/{orderId}/items")
    public Envelope<OrderResponse> addItem(@PathVariable Long orderId,
                                           @RequestBody AddItemRequest request,
                                           HttpSession session) {
        Long customerId = customer(session).getId();
        int quantity = request.quantity() == null ? 1 : request.quantity();
        orderService.addPizzaToOrder(orderId, request.pizzaId(), quantity, customerId);
        return Envelope.success("Pizza added to the order successfully.", reload(orderId, customerId));
    }

    @PostMapping("/{orderId}/items/{itemId}/increase")
    public Envelope<OrderResponse> increaseItem(@PathVariable Long orderId, @PathVariable Long itemId,
                                                HttpSession session) {
        Long customerId = customer(session).getId();
        orderService.increaseItemQuantity(orderId, itemId, customerId);
        return Envelope.success("Pizza quantity updated successfully.", reload(orderId, customerId));
    }

    @PostMapping("/{orderId}/items/{itemId}/decrease")
    public Envelope<OrderResponse> decreaseItem(@PathVariable Long orderId, @PathVariable Long itemId,
                                                HttpSession session) {
        Long customerId = customer(session).getId();
        orderService.decreaseItemQuantity(orderId, itemId, customerId);
        return Envelope.success("Pizza quantity updated successfully.", reload(orderId, customerId));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public Envelope<OrderResponse> removeItem(@PathVariable Long orderId, @PathVariable Long itemId,
                                              HttpSession session) {
        Long customerId = customer(session).getId();
        orderService.removeOrderItem(orderId, itemId, customerId);
        return Envelope.success("Item removed from the order.", reload(orderId, customerId));
    }

    private OrderResponse reload(Long orderId, Long customerId) {
        return ApiMappers.order(orderService.findOrderById(orderId, customerId));
    }

    private Customer customer(HttpSession session) {
        return SessionUtil.getCurrentCustomer(session);
    }
}
