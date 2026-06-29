package com.pizza.controller;
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
import com.pizza.entity.Customer;
import com.pizza.entity.Order;
import com.pizza.entity.Pizza;
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
        orderDTO.setPizzaId(pizzaId);
        orderDTO.setQuantity(1);
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
            model.addAttribute("pizza", pizzaService.findById(orderDTO.getPizzaId()));
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
@GetMapping("/edit/{orderId}")
public String showEditOrderForm(
        @PathVariable Long orderId,
        HttpSession session,
        Model model,
        RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please log in first.");
        return "redirect:/login";
    }

    Order order = orderService.getOrderForEdit(orderId, customer.getId());

    OrderDTO orderDTO = new OrderDTO();
    orderDTO.setPizzaId(order.getPizza().getId());
    orderDTO.setQuantity(order.getQuantity());
    orderDTO.setDeliveryAddress(order.getDeliveryAddress());
    orderDTO.setPhone(order.getPhone());

    model.addAttribute("order", order);
    model.addAttribute("orderDTO", orderDTO);

    return "edit-order";
}
@PostMapping("/update/{orderId}")
public String updateOrder(
        @PathVariable Long orderId,
        @Valid @ModelAttribute("orderDTO") OrderDTO orderDTO,
        BindingResult bindingResult,
        HttpSession session,
        Model model,
        RedirectAttributes redirectAttributes) {

    Customer customer = SessionUtil.getCurrentCustomer(session);

    if (customer == null) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please log in first.");
        return "redirect:/login";
    }

    if (bindingResult.hasErrors()) {

        Order order = orderService.getOrderForEdit(orderId, customer.getId());

        model.addAttribute("order", order);

        return "edit-order";
    }

    orderService.updateOrder(orderId, orderDTO, customer.getId());

    redirectAttributes.addFlashAttribute(
            "successMessage",
            "Order updated successfully.");

    return "redirect:/orders/history";
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
}
