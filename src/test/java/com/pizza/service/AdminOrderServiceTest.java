package com.pizza.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link AdminOrderService} (US-017, US-018):
 * admin-driven order status transitions, plus (Task 9) the additive
 * search/filter/sort dispatch that mirrors {@link PizzaService#search}.
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private AdminOrderService adminOrderService;

    // ---------------------------------------------------------------- search dispatch

    @Test
    void search_withNoFilters_dispatchesToFindAllOrdered() {
        List<Order> all = List.of(TestDataFactory.order(TestDataFactory.customer()));
        when(orderRepository.findAllOrdered()).thenReturn(all);

        List<Order> result = adminOrderService.search(null, null, null);

        assertThat(result).isEqualTo(all);
        verify(orderRepository).findAllOrdered();
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void search_withNoFilters_matchesFindAllOutput() {
        // Hard constraint from the brief: search(null, null, null) must
        // produce exactly what the untouched findAll() returns.
        List<Order> all = List.of(TestDataFactory.order(TestDataFactory.customer()));
        when(orderRepository.findAllOrdered()).thenReturn(all);

        assertThat(adminOrderService.search(null, null, null)).isEqualTo(adminOrderService.findAll());
    }

    @Test
    void search_bySearchTermOnly_dispatchesToSearchByOrderNumberOrCustomerName() {
        List<Order> matches = List.of(TestDataFactory.order(TestDataFactory.customer()));
        when(orderRepository.searchByOrderNumberOrCustomerName("jane")).thenReturn(matches);

        List<Order> result = adminOrderService.search("jane", null, null);

        assertThat(result).isEqualTo(matches);
        verify(orderRepository).searchByOrderNumberOrCustomerName("jane");
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void search_withSearchTerm_trimsWhitespaceBeforeQuerying() {
        List<Order> matches = List.of(TestDataFactory.order(TestDataFactory.customer()));
        when(orderRepository.searchByOrderNumberOrCustomerName("jane")).thenReturn(matches);

        adminOrderService.search("  jane  ", null, null);

        verify(orderRepository).searchByOrderNumberOrCustomerName("jane");
    }

    @Test
    void search_byStatusOnly_dispatchesToFindByStatus() {
        List<Order> matches = List.of(
                TestDataFactory.order(TestDataFactory.customer(), LocalDateTime.now(), "PROCESSING"));
        when(orderRepository.findByStatus("PROCESSING")).thenReturn(matches);

        List<Order> result = adminOrderService.search(null, "PROCESSING", null);

        assertThat(result).isEqualTo(matches);
        verify(orderRepository).findByStatus("PROCESSING");
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void search_bySearchTermAndStatus_dispatchesToCombinedQuery() {
        List<Order> matches = List.of(TestDataFactory.order(TestDataFactory.customer()));
        when(orderRepository.searchByTermAndStatus("jane", "PROCESSING")).thenReturn(matches);

        List<Order> result = adminOrderService.search("jane", "PROCESSING", null);

        assertThat(result).isEqualTo(matches);
        verify(orderRepository).searchByTermAndStatus("jane", "PROCESSING");
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void search_withBlankSearchAndStatus_treatsBlankAsAbsent_dispatchesToFindAllOrdered() {
        List<Order> all = List.of(TestDataFactory.order(TestDataFactory.customer()));
        when(orderRepository.findAllOrdered()).thenReturn(all);

        List<Order> result = adminOrderService.search("   ", "", null);

        assertThat(result).isEqualTo(all);
        verify(orderRepository).findAllOrdered();
        verifyNoMoreInteractions(orderRepository);
    }

    // ---------------------------------------------------------------- search sort

    private Order orderWithTotalAndCreatedAt(BigDecimal total, LocalDateTime createdAt) {
        Order order = TestDataFactory.order(TestDataFactory.customer(), createdAt, "PLACED",
                total, new BigDecimal("1.00"), total);
        order.setCreatedAt(createdAt);
        return order;
    }

    @Test
    void search_sortsByTotalAscending() {
        Order cheap = orderWithTotalAndCreatedAt(new BigDecimal("11.00"), LocalDateTime.now());
        Order pricey = orderWithTotalAndCreatedAt(new BigDecimal("55.00"), LocalDateTime.now());
        when(orderRepository.findAllOrdered()).thenReturn(new ArrayList<>(List.of(pricey, cheap)));

        List<Order> result = adminOrderService.search(null, null, "totalAsc");

        assertThat(result).containsExactly(cheap, pricey);
    }

    @Test
    void search_sortsByTotalDescending() {
        Order cheap = orderWithTotalAndCreatedAt(new BigDecimal("11.00"), LocalDateTime.now());
        Order pricey = orderWithTotalAndCreatedAt(new BigDecimal("55.00"), LocalDateTime.now());
        when(orderRepository.findAllOrdered()).thenReturn(new ArrayList<>(List.of(cheap, pricey)));

        List<Order> result = adminOrderService.search(null, null, "totalDesc");

        assertThat(result).containsExactly(pricey, cheap);
    }

    @Test
    void search_sortsByOldestFirst() {
        LocalDateTime earlier = LocalDateTime.now().minusDays(1);
        LocalDateTime later = LocalDateTime.now();
        Order older = orderWithTotalAndCreatedAt(new BigDecimal("20.00"), earlier);
        Order newer = orderWithTotalAndCreatedAt(new BigDecimal("20.00"), later);
        // Repository already returns createdAt DESC (newer first); "oldest" sort must reverse that.
        when(orderRepository.findAllOrdered()).thenReturn(new ArrayList<>(List.of(newer, older)));

        List<Order> result = adminOrderService.search(null, null, "oldest");

        assertThat(result).containsExactly(older, newer);
    }

    @Test
    void search_withNewestOrNoSort_leavesRepositoryOrderUnchanged() {
        LocalDateTime earlier = LocalDateTime.now().minusDays(1);
        LocalDateTime later = LocalDateTime.now();
        Order newer = orderWithTotalAndCreatedAt(new BigDecimal("20.00"), later);
        Order older = orderWithTotalAndCreatedAt(new BigDecimal("20.00"), earlier);
        // Every dispatch branch already returns createdAt DESC, so "newest" (or
        // no sort at all) must be a no-op re-sort.
        when(orderRepository.findAllOrdered()).thenReturn(new ArrayList<>(List.of(newer, older)));

        assertThat(adminOrderService.search(null, null, "newest")).containsExactly(newer, older);
        assertThat(adminOrderService.search(null, null, null)).containsExactly(newer, older);
    }

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

    // ---------------------------------------------------------------- bulkUpdateStatus

    private Order orderWithIdAndStatus(Long id, String status) {
        Order order = TestDataFactory.order(TestDataFactory.customer());
        order.setId(id);
        order.setStatus(status);
        return order;
    }

    @Test
    void bulkUpdateStatus_allEligible_updatesAllAndSkipsNone() {
        Order o1 = orderWithIdAndStatus(1L, "PLACED");
        Order o2 = orderWithIdAndStatus(2L, "PLACED");
        when(orderRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(o1));
        when(orderRepository.findByIdWithDetails(2L)).thenReturn(Optional.of(o2));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminOrderService.BulkStatusUpdateResult result =
                adminOrderService.bulkUpdateStatus(List.of(1L, 2L), "PROCESSING");

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.skippedOrderNumbers()).isEmpty();
        assertThat(o1.getStatus()).isEqualTo("PROCESSING");
        assertThat(o2.getStatus()).isEqualTo("PROCESSING");
        verify(orderRepository).save(o1);
        verify(orderRepository).save(o2);
    }

    @Test
    void bulkUpdateStatus_mixedBatch_updatesEligibleAndSkipsIneligibleByOrderNumber() {
        // PLACED -> PROCESSING is valid; DELIVERED is terminal (no valid transitions).
        Order eligible1 = orderWithIdAndStatus(1L, "PLACED");
        Order ineligible = orderWithIdAndStatus(2L, "DELIVERED");
        Order eligible2 = orderWithIdAndStatus(3L, "PLACED");
        when(orderRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(eligible1));
        when(orderRepository.findByIdWithDetails(2L)).thenReturn(Optional.of(ineligible));
        when(orderRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(eligible2));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminOrderService.BulkStatusUpdateResult result =
                adminOrderService.bulkUpdateStatus(List.of(1L, 2L, 3L), "PROCESSING");

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.skippedOrderNumbers()).containsExactly(ineligible.getOrderNumber());
        assertThat(eligible1.getStatus()).isEqualTo("PROCESSING");
        assertThat(eligible2.getStatus()).isEqualTo("PROCESSING");
        assertThat(ineligible.getStatus()).isEqualTo("DELIVERED");
        verify(orderRepository, never()).save(ineligible);
    }

    @Test
    void bulkUpdateStatus_allIneligible_updatesNoneAndSkipsAll() {
        Order o1 = orderWithIdAndStatus(1L, "DELIVERED");
        Order o2 = orderWithIdAndStatus(2L, "CANCELLED");
        when(orderRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(o1));
        when(orderRepository.findByIdWithDetails(2L)).thenReturn(Optional.of(o2));

        AdminOrderService.BulkStatusUpdateResult result =
                adminOrderService.bulkUpdateStatus(List.of(1L, 2L), "PROCESSING");

        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.skippedOrderNumbers()).containsExactlyInAnyOrder(o1.getOrderNumber(), o2.getOrderNumber());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void bulkUpdateStatus_unknownTargetStatus_throwsImmediately_beforeTouchingAnyOrder() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adminOrderService.bulkUpdateStatus(List.of(1L, 2L), "NOT_A_STATUS"));

        assertThat(ex.getMessage()).isEqualTo("Unknown target status: NOT_A_STATUS");
        verify(orderRepository, never()).save(any());
        verify(orderRepository, never()).findByIdWithDetails(any());
    }

    @Test
    void bulkUpdateStatus_emptyOrderIdsList_returnsZeroUpdatedAndNoSkips_withoutError() {
        AdminOrderService.BulkStatusUpdateResult result =
                adminOrderService.bulkUpdateStatus(List.of(), "PROCESSING");

        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.skippedOrderNumbers()).isEmpty();
        verify(orderRepository, never()).save(any());
    }
}
