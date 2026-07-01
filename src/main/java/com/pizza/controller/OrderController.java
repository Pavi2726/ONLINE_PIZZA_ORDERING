package com.pizza.controller;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pizza.dto.OrderDTO;
import com.pizza.entity.Cart;
import com.pizza.entity.Coupon;
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
import com.pizza.service.CartService;
import com.pizza.service.OrderService;
import com.pizza.service.PizzaService;
import com.pizza.util.SessionUtil;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Order placement and confirmation (US-007). Requires a logged-in customer.
 */
@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PizzaService pizzaService;
    private final CartService cartService;

    /** Shows the order form pre-filled from the logged-in customer. */
    @GetMapping("/new")
    public String showOrderForm(
            @RequestParam("pizzaId") Long pizzaId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Customer customer = SessionUtil.getCurrentCustomer(session);
        if (customer == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please log in to place an order.");
            return "redirect:/login";
        }

        Pizza pizza = pizzaService.findById(pizzaId);
        if (!pizza.isAvailable()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This pizza is currently unavailable.");
            return "redirect:/pizzas";
        }
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setDeliveryAddress(customer.getAddress());
        orderDTO.setPhone(customer.getPhone());

        model.addAttribute("orderDTO", orderDTO);
        model.addAttribute("pizza", pizza);
        return "place-order";
    }

    /** Places the order and redirects to the confirmation page. */
    @PostMapping
    public String placeOrder(
            @Valid @ModelAttribute("orderDTO") OrderDTO orderDTO,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Customer customer = SessionUtil.getCurrentCustomer(session);
        if (customer == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please log in to place an order.");
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            return "place-order";
        }

        Order order = orderService.placeOrder(orderDTO, customer);
        return "redirect:/orders/success/" + order.getOrderNumber();
    }

    /** Order confirmation page (US-007). Only the placing customer may view it. */
    @GetMapping("/success/{orderNumber}")
    public String orderSuccess(
            @PathVariable String orderNumber,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        Customer customer = SessionUtil.getCurrentCustomer(session);
        if (customer == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please log in to view your order.");
            return "redirect:/login";
        }

        Order order = orderService.findByOrderNumberForCustomer(orderNumber, customer.getId());
        model.addAttribute("order", order);
        return "order-success";
    }
    @GetMapping("/history")
public String viewOrderHistory(
        HttpSession session,
        Model model,
        RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please log in to view your order history.");
        return "redirect:/login";
    }

    List<Order> orders = orderService.getOrderHistory(customer.getId());

    model.addAttribute("orders", orders);

    return "order-history";
}
@PostMapping("/cancel/{orderId}")
public String cancelOrder(
        @PathVariable Long orderId,
        HttpSession session,
        RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please log in first.");
        return "redirect:/login";
    }

    orderService.cancelOrder(orderId, customer.getId());

    redirectAttributes.addFlashAttribute(
            "successMessage",
            "Order cancelled successfully.");

    return "redirect:/orders/history";
}
@GetMapping("/checkout")
public String checkout(HttpSession session,
                       Model model,
                       RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        return "redirect:/login";
    }

    Cart cart = cartService.getCart(customer.getEmail());

    if (cart.getCartItems().isEmpty()) {

        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Your cart is empty.");

        return "redirect:/cart";
    }

    BigDecimal subtotal = cartService.getCartSubtotal(customer.getEmail());

    Coupon coupon = (Coupon) session.getAttribute("appliedCoupon");

    BigDecimal discount = BigDecimal.ZERO;

    if (coupon != null) {

        discount = subtotal
                .multiply(BigDecimal.valueOf(coupon.getDiscountPercentage()))
                .divide(BigDecimal.valueOf(100));
    }

    BigDecimal grandTotal = subtotal.subtract(discount);

    model.addAttribute("cart", cart);
    model.addAttribute("subtotal", subtotal);
    model.addAttribute("discount", discount);
    model.addAttribute("grandTotal", grandTotal);
    model.addAttribute("appliedCoupon", coupon);

    return "checkout";
}
@PostMapping("/place")
public String placeCartOrder(HttpSession session,
                             RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please log in first.");
        return "redirect:/login";
    }

    OrderDTO dto = new OrderDTO();
    dto.setDeliveryAddress(customer.getAddress());
    dto.setPhone(customer.getPhone());

    Coupon coupon = (Coupon) session.getAttribute("appliedCoupon");
    if (coupon != null) {
        dto.setCouponCode(coupon.getCouponCode());
    }

    Order order = orderService.placeOrder(dto, customer);

    session.removeAttribute("appliedCoupon");

    redirectAttributes.addFlashAttribute(
            "successMessage",
            "Order placed successfully!");

    return "redirect:/orders/success/" + order.getOrderNumber();
}
@GetMapping("/edit/{orderId}")
public String showEditOrderPage(@PathVariable Long orderId,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please login first.");
        return "redirect:/login";
    }
Order order = orderService.findOrderById(orderId, customer.getId());

if (order.getOrderTime() == null ||
    Duration.between(order.getOrderTime(), LocalDateTime.now()).toMinutes() >= 5) {

    redirectAttributes.addFlashAttribute(
            "errorMessage",
            "The edit time for this order has expired. Please place a new order to make changes.");

    return "redirect:/orders/history";
}
String editingAddress = (String) session.getAttribute("editingAddress");
String editingPhone = (String) session.getAttribute("editingPhone");

if (editingAddress != null) {
    order.setDeliveryAddress(editingAddress);
}

if (editingPhone != null) {
    order.setPhone(editingPhone);
}

model.addAttribute("order", order);

return "edit-order";
} 
@PostMapping("/edit/{orderId}/increase/{itemId}")
public String increaseQuantity(@PathVariable Long orderId,
                               @PathVariable Long itemId,
                               @RequestParam(required = false) String deliveryAddress,
                               @RequestParam(required = false) String phone,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute("errorMessage", "Please login first.");
        return "redirect:/login";
    }
if (deliveryAddress != null) {
    session.setAttribute("editingAddress", deliveryAddress);
}

if (phone != null) {
    session.setAttribute("editingPhone", phone);
}
    orderService.increaseItemQuantity(orderId, itemId, customer.getId());
redirectAttributes.addFlashAttribute(
        "successMessage",
        "Pizza quantity updated successfully.");

    return "redirect:/orders/edit/" + orderId;
}

@PostMapping("/edit/{orderId}/decrease/{itemId}")
public String decreaseQuantity(@PathVariable Long orderId,
                               @PathVariable Long itemId,
                               @RequestParam(required = false) String deliveryAddress,
                               @RequestParam(required = false) String phone,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute("errorMessage", "Please login first.");
        return "redirect:/login";
    }

    if (deliveryAddress != null) {
        session.setAttribute("editingAddress", deliveryAddress);
    }

    if (phone != null) {
        session.setAttribute("editingPhone", phone);
    }

    orderService.decreaseItemQuantity(orderId, itemId, customer.getId());

    return "redirect:/orders/edit/" + orderId;
}@PostMapping("/edit/{orderId}/add-pizza")
public String addPizzaToOrder(@PathVariable Long orderId,
                              @RequestParam Long pizzaId,
                              @RequestParam(defaultValue = "1") Integer quantity,
                              @RequestParam(required = false) String deliveryAddress,
                              @RequestParam(required = false) String phone,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please login first.");
        return "redirect:/login";
    }

    if (deliveryAddress != null) {
        session.setAttribute("editingAddress", deliveryAddress);
    }

    if (phone != null) {
        session.setAttribute("editingPhone", phone);
    }

    orderService.addPizzaToOrder(
            orderId,
            pizzaId,
            quantity,
            customer.getId());

    redirectAttributes.addFlashAttribute(
            "successMessage",
            "Pizza added to the order successfully.");

    return "redirect:/orders/edit/" + orderId;
}
@PostMapping("/edit/{orderId}")
public String updateOrder(@PathVariable Long orderId,
                          @RequestParam String deliveryAddress,
                          @RequestParam String phone,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please login first.");
        return "redirect:/login";
    }

    orderService.updateOrderDetails(
            orderId,
            deliveryAddress,
            phone,
            customer.getId());

    redirectAttributes.addFlashAttribute(
            "successMessage",
            "Order updated successfully.");
session.removeAttribute("editingAddress");
session.removeAttribute("editingPhone");
    return "redirect:/orders/history";
}
@PostMapping("/edit/{orderId}/remove/{itemId}")
public String removeItem(@PathVariable Long orderId,
                         @PathVariable Long itemId,
                         @RequestParam(required = false) String deliveryAddress,
                         @RequestParam(required = false) String phone,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute("errorMessage", "Please login first.");
        return "redirect:/login";
    }

    if (deliveryAddress != null) {
        session.setAttribute("editingAddress", deliveryAddress);
    }

    if (phone != null) {
        session.setAttribute("editingPhone", phone);
    }

    orderService.removeOrderItem(orderId, itemId, customer.getId());

    return "redirect:/orders/edit/" + orderId;
}
}
