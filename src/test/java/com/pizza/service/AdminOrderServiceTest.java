package com.pizza.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pizza.entity.Order;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.OrderRepository;
import com.pizza.testsupport.TestDataFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link AdminOrderService} (US-017, US-018):
 * admin-driven order status transitions.
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private AdminOrderService adminOrderService;

    // ---------------------------------------------------------------- getById

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        when(orderRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> adminOrderService.getById(99L));
        assertThat(ex.getMessage()).isEqualTo("Order not found");
    }

    // ---------------------------------------------------------------- updateStatus

    @Test
    void updateStatus_validTransition_persistsNewStatus() {
        Order order = TestDataFactory.order(TestDataFactory.customer());
        order.setId(1L);
        order.setStatus("PLACED");
        when(orderRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = adminOrderService.updateStatus(1L, "PROCESSING");

        assertThat(result.getStatus()).isEqualTo("PROCESSING");
        verify(orderRepository).save(order);
    }

    @Test
    void updateStatus_disallowedTransition_throwsIllegalStateExceptionAndDoesNotSave() {
        // PLACED can only move to PROCESSING or CANCELLED per OrderStatus - not DELIVERED.
        Order order = TestDataFactory.order(TestDataFactory.customer());
        order.setId(2L);
        order.setStatus("PLACED");
        when(orderRepository.findByIdWithDetails(2L)).thenReturn(Optional.of(order));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adminOrderService.updateStatus(2L, "DELIVERED"));

        assertThat(ex.getMessage()).isEqualTo("Cannot move an order from PLACED to DELIVERED.");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateStatus_terminalStatus_hasNoAllowedTransitionsAndThrows() {
        // DELIVERED's allowedNextStatuses set is empty, so every target is disallowed.
        Order order = TestDataFactory.order(TestDataFactory.customer());
        order.setId(3L);
        order.setStatus("DELIVERED");
        when(orderRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> adminOrderService.updateStatus(3L, "PROCESSING"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateStatus_unknownTargetStatus_throwsIllegalArgumentException() {
        Order order = TestDataFactory.order(TestDataFactory.customer());
        order.setId(4L);
        order.setStatus("PLACED");
        when(orderRepository.findByIdWithDetails(4L)).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adminOrderService.updateStatus(4L, "NOT_A_STATUS"));

        assertThat(ex.getMessage()).isEqualTo("Unknown target status: NOT_A_STATUS");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateStatus_orderInUnrecognizedCurrentStatus_throwsIllegalStateException() {
        Order order = TestDataFactory.order(TestDataFactory.customer());
        order.setId(5L);
        order.setStatus("BOGUS_LEGACY_STATUS");
        when(orderRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> adminOrderService.updateStatus(5L, "PROCESSING"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateStatus_orderNotFound_throwsResourceNotFoundException() {
        when(orderRepository.findByIdWithDetails(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminOrderService.updateStatus(404L, "PROCESSING"));
        verify(orderRepository, never()).save(any());
    }
}
