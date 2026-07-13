package com.pizza.api;

import com.pizza.api.dto.ApiResponses.Envelope;
import com.pizza.api.dto.ApiResponses.OrderResponse;
import com.pizza.entity.Order;
import com.pizza.service.AdminOrderService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin order list, detail, and single/bulk status transitions. */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderApiController {

    private final AdminOrderService adminOrderService;

    public record StatusUpdateRequest(String targetStatus) {
    }

    public record BulkStatusRequest(List<Long> orderIds, String targetStatus) {
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) String search,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String sort) {
        return ApiMappers.ordersForAdmin(adminOrderService.search(search, status, sort));
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable Long id) {
        return ApiMappers.orderForAdmin(adminOrderService.getById(id));
    }

    @PostMapping("/{id}/status")
    public Envelope<OrderResponse> updateStatus(@PathVariable Long id,
                                                @RequestBody StatusUpdateRequest request) {
        Order order = adminOrderService.updateStatus(id, request.targetStatus());
        return Envelope.success(
                "Order \"" + order.getOrderNumber() + "\" is now " + order.getStatus() + ".",
                ApiMappers.orderForAdmin(order));
    }

    /**
     * A partial success — some orders skipped because the transition was invalid for
     * their current status — is reported as a warning, not a success, exactly as the
     * server-rendered flow did.
     */
    @PostMapping("/bulk-status")
    public Envelope<Void> bulkUpdateStatus(@RequestBody BulkStatusRequest request) {
        AdminOrderService.BulkStatusUpdateResult result =
                adminOrderService.bulkUpdateStatus(request.orderIds(), request.targetStatus());

        String message = result.summaryMessage(request.targetStatus());
        return result.skippedOrderNumbers().isEmpty()
                ? Envelope.success(message, null)
                : Envelope.warning(message, null);
    }
}
