package com.pizza.service;

import com.pizza.entity.Drink;
import com.pizza.repository.CartItemRepository;
import com.pizza.repository.DrinkRepository;
import com.pizza.repository.OrderItemRepository;
import com.pizza.testsupport.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link DrinkService#delete}, mirroring
 * {@code PizzaServiceTest}'s delete coverage: blocked while any referencing
 * order is still active, allowed (and reference-clearing) once every
 * referencing order is terminal, and Cloudinary cleanup is best-effort.
 */
@ExtendWith(MockitoExtension.class)
class DrinkServiceTest {

    @Mock
    private DrinkRepository drinkRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private DrinkService drinkService;

    @Test
    void delete_clearsReferencesThenDeletesAndFlushesRepositoryBeforeDeletingCloudinaryImage() {
        Drink drink = TestDataFactory.drink();
        drink.setId(3L);
        drink.setImagePublicId("drinks/to-delete");
        when(drinkRepository.findById(3L)).thenReturn(Optional.of(drink));
        when(orderItemRepository.existsByDrink_IdAndOrder_StatusNotIn(any(Long.class), any())).thenReturn(false);

        drinkService.delete(3L);

        InOrder order = inOrder(cartItemRepository, orderItemRepository, drinkRepository, cloudinaryService);
        order.verify(cartItemRepository).deleteByDrinkId(3L);
        order.verify(orderItemRepository).clearDrinkReferences(3L);
        order.verify(drinkRepository).delete(drink);
        order.verify(drinkRepository).flush();
        order.verify(cloudinaryService).delete("drinks/to-delete");
    }

    @Test
    void delete_blocksAndNeverTouchesAnythingElse_whenDrinkIsOnAnActiveOrder() {
        Drink drink = TestDataFactory.drink();
        drink.setId(3L);
        drink.setImagePublicId("drinks/to-delete");
        when(drinkRepository.findById(3L)).thenReturn(Optional.of(drink));
        when(orderItemRepository.existsByDrink_IdAndOrder_StatusNotIn(any(Long.class), any())).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> drinkService.delete(3L));

        assertThat(ex.getMessage()).isEqualTo(
                "This drink cannot be deleted because it is part of an order that has not been delivered or cancelled yet.");
        verify(drinkRepository, never()).delete(any());
        verify(cartItemRepository, never()).deleteByDrinkId(any());
        verify(orderItemRepository, never()).clearDrinkReferences(any());
        verify(cloudinaryService, never()).delete(any());
    }

    @Test
    void delete_stillDeletesTheDrink_whenCloudinaryCleanupThrows() {
        // Image cleanup is best-effort: a Cloudinary failure must not roll back
        // an otherwise-successful drink deletion (mirrors PizzaServiceTest).
        Drink drink = TestDataFactory.drink();
        drink.setId(3L);
        drink.setImagePublicId("drinks/to-delete");
        when(drinkRepository.findById(3L)).thenReturn(Optional.of(drink));
        when(orderItemRepository.existsByDrink_IdAndOrder_StatusNotIn(any(Long.class), any())).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("Cloudinary is unreachable"))
                .when(cloudinaryService).delete("drinks/to-delete");

        drinkService.delete(3L);

        verify(drinkRepository).delete(drink);
        verify(drinkRepository).flush();
    }
}
