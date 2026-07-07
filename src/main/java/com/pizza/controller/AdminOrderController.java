package com.pizza.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pizza.entity.Order;
import com.pizza.service.AdminOrderService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderController.class);

    private final AdminOrderService adminOrderService;

    // List All Orders (with search/filter/sort, Task 9)
    @GetMapping
    public String list(@RequestParam(required = false) String search,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String sort,
                        Model model) {
        List<Order> orders = adminOrderService.search(search, status, sort);
        model.addAttribute("orders", orders);
        model.addAttribute("search", search);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("sort", sort);
        return "admin-order-list";
    }

    // Order Detail
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Order order = adminOrderService.getById(id);
        model.addAttribute("order", order);
        return "admin-order-detail";
    }

    // Update Order Status
    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable Long id,
            @RequestParam String targetStatus,
            RedirectAttributes redirectAttributes) {

        try {
            Order order = adminOrderService.updateStatus(id, targetStatus);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Order \"" + order.getOrderNumber() + "\" is now " + order.getStatus() + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error updating order {} status", id, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Something went wrong updating the order status. Please try again.");
        }

        return "redirect:/admin/orders/" + id;
    }

    // Bulk Update Order Status (Task 10)
    @PostMapping("/bulk-status")
    public String bulkUpdateStatus(
            @RequestParam List<Long> orderIds,
            @RequestParam String targetStatus,
            RedirectAttributes redirectAttributes) {

        try {
            AdminOrderService.BulkStatusUpdateResult result =
                    adminOrderService.bulkUpdateStatus(orderIds, targetStatus);

            if (result.skippedOrderNumbers().isEmpty()) {
                redirectAttributes.addFlashAttribute("successMessage", result.summaryMessage(targetStatus));
            } else {
                redirectAttributes.addFlashAttribute("warningMessage", result.summaryMessage(targetStatus));
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during bulk order status update", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Something went wrong updating order statuses. Please try again.");
        }

        return "redirect:/admin/orders";
    }
}
