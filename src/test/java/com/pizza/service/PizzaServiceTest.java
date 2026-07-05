package com.pizza.service;

import com.pizza.dto.PizzaDTO;
import com.pizza.entity.Pizza;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.PizzaRepository;
import com.pizza.service.CloudinaryService.UploadResult;
import com.pizza.testsupport.TestDataFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link PizzaService}: catalogue search/sort
 * dispatch and Cloudinary-backed add/update/delete.
 */
@ExtendWith(MockitoExtension.class)
class PizzaServiceTest {

    @Mock
    private PizzaRepository pizzaRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private PizzaService pizzaService;

    private PizzaDTO dto(String name, String category, String price, boolean available) {
        PizzaDTO dto = new PizzaDTO();
        dto.setName(name);
        dto.setDescription("Test description");
        dto.setCategory(category);
        dto.setPrice(new BigDecimal(price));
        dto.setAvailable(available);
        return dto;
    }

    // ---------------------------------------------------------------- search dispatch

    @Test
    void search_withNoFilters_dispatchesToFindAll() {
        List<Pizza> all = List.of(TestDataFactory.pizza());
        when(pizzaRepository.findAll()).thenReturn(all);

        List<Pizza> result = pizzaService.search(null, null, null);

        assertThat(result).isEqualTo(all);
        verify(pizzaRepository).findAll();
        verifyNoMoreInteractions(pizzaRepository);
    }

    @Test
    void search_byCategoryOnly_dispatchesToFindByCategory() {
        List<Pizza> matches = List.of(TestDataFactory.pizza("Veg Supreme", new BigDecimal("8.00"), "Veg", true));
        when(pizzaRepository.findByCategory("Veg")).thenReturn(matches);

        List<Pizza> result = pizzaService.search(null, "Veg", null);

        assertThat(result).isEqualTo(matches);
        verify(pizzaRepository).findByCategory("Veg");
        verifyNoMoreInteractions(pizzaRepository);
    }

    @Test
    void search_bySearchTermOnly_dispatchesToFindByName() {
        List<Pizza> matches = List.of(TestDataFactory.pizza("Margherita", new BigDecimal("9.99"), "Classic", true));
        when(pizzaRepository.findByNameContainingIgnoreCase("marg")).thenReturn(matches);

        List<Pizza> result = pizzaService.search("marg", null, null);

        assertThat(result).isEqualTo(matches);
        verify(pizzaRepository).findByNameContainingIgnoreCase("marg");
        verifyNoMoreInteractions(pizzaRepository);
    }

    @Test
    void search_bySearchTermAndCategory_dispatchesToCombinedQuery() {
        List<Pizza> matches = List.of(TestDataFactory.pizza("Veg Margherita", new BigDecimal("9.99"), "Veg", true));
        when(pizzaRepository.findByCategoryAndNameContainingIgnoreCase("Veg", "marg")).thenReturn(matches);

        List<Pizza> result = pizzaService.search("marg", "Veg", null);

        assertThat(result).isEqualTo(matches);
        verify(pizzaRepository).findByCategoryAndNameContainingIgnoreCase("Veg", "marg");
        verifyNoMoreInteractions(pizzaRepository);
    }

    @Test
    void search_sortsByPriceAscending() {
        Pizza cheap = TestDataFactory.pizza("Cheap", new BigDecimal("5.00"), "Classic", true);
        Pizza pricey = TestDataFactory.pizza("Pricey", new BigDecimal("15.00"), "Classic", true);
        when(pizzaRepository.findAll()).thenReturn(new ArrayList<>(List.of(pricey, cheap)));

        List<Pizza> result = pizzaService.search(null, null, "priceAsc");

        assertThat(result).containsExactly(cheap, pricey);
    }

    @Test
    void search_sortsByPriceDescending() {
        Pizza cheap = TestDataFactory.pizza("Cheap", new BigDecimal("5.00"), "Classic", true);
        Pizza pricey = TestDataFactory.pizza("Pricey", new BigDecimal("15.00"), "Classic", true);
        when(pizzaRepository.findAll()).thenReturn(new ArrayList<>(List.of(cheap, pricey)));

        List<Pizza> result = pizzaService.search(null, null, "priceDesc");

        assertThat(result).containsExactly(pricey, cheap);
    }

    // ---------------------------------------------------------------- add

    @Test
    void add_uploadsImageThenSaves() {
        PizzaDTO dto = dto("Pepperoni", "Meat", "11.50", true);
        MockMultipartFile image = new MockMultipartFile("image", "pep.png", "image/png", new byte[]{1, 2, 3});
        UploadResult uploadResult = new UploadResult("https://cdn.test/pep.png", "pizzas/pep-1");
        when(cloudinaryService.upload(image)).thenReturn(uploadResult);
        when(pizzaRepository.saveAndFlush(any(Pizza.class))).thenAnswer(inv -> {
            Pizza p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });

        Pizza result = pizzaService.add(dto, image);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getName()).isEqualTo("Pepperoni");
        assertThat(result.getImageUrl()).isEqualTo("https://cdn.test/pep.png");
        assertThat(result.getImagePublicId()).isEqualTo("pizzas/pep-1");

        InOrder order = inOrder(cloudinaryService, pizzaRepository);
        order.verify(cloudinaryService).upload(image);
        order.verify(pizzaRepository).saveAndFlush(any(Pizza.class));
    }

    @Test
    void add_deletesUploadedImage_whenSaveFails() {
        PizzaDTO dto = dto("Pepperoni", "Meat", "11.50", true);
        MockMultipartFile image = new MockMultipartFile("image", "pep.png", "image/png", new byte[]{1, 2, 3});
        UploadResult uploadResult = new UploadResult("https://cdn.test/pep.png", "pizzas/pep-1");
        when(cloudinaryService.upload(image)).thenReturn(uploadResult);
        when(pizzaRepository.saveAndFlush(any(Pizza.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> pizzaService.add(dto, image));

        verify(cloudinaryService).delete("pizzas/pep-1");
    }

    // ---------------------------------------------------------------- update

    @Test
    void update_replacingImage_deletesOldImageOnlyAfterSaveSucceeds() {
        Pizza existing = TestDataFactory.pizza("Old Name", new BigDecimal("10.00"), "Classic", true);
        existing.setId(7L);
        existing.setImagePublicId("pizzas/old-1");
        when(pizzaRepository.findById(7L)).thenReturn(Optional.of(existing));

        PizzaDTO dto = dto("New Name", "Classic", "12.00", true);
        MockMultipartFile newImage = new MockMultipartFile("image", "new.png", "image/png", new byte[]{9, 9});
        UploadResult newUpload = new UploadResult("https://cdn.test/new.png", "pizzas/new-1");
        when(cloudinaryService.upload(newImage)).thenReturn(newUpload);
        when(pizzaRepository.saveAndFlush(existing)).thenReturn(existing);

        Pizza result = pizzaService.update(7L, dto, newImage);

        assertThat(result.getImageUrl()).isEqualTo("https://cdn.test/new.png");
        assertThat(result.getImagePublicId()).isEqualTo("pizzas/new-1");

        InOrder order = inOrder(cloudinaryService, pizzaRepository);
        order.verify(cloudinaryService).upload(newImage);
        order.verify(pizzaRepository).saveAndFlush(existing);
        order.verify(cloudinaryService).delete("pizzas/old-1");
        verify(cloudinaryService, never()).delete("pizzas/new-1");
    }

    @Test
    void update_withoutNewImage_neverTouchesCloudinary() {
        Pizza existing = TestDataFactory.pizza("Old Name", new BigDecimal("10.00"), "Classic", true);
        existing.setId(7L);
        existing.setImagePublicId("pizzas/old-1");
        when(pizzaRepository.findById(7L)).thenReturn(Optional.of(existing));

        PizzaDTO dto = dto("New Name", "Classic", "12.00", false);
        when(pizzaRepository.saveAndFlush(existing)).thenReturn(existing);

        Pizza result = pizzaService.update(7L, dto, null);

        assertThat(result.getImagePublicId()).isEqualTo("pizzas/old-1");
        assertThat(result.isAvailable()).isFalse();
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    void update_keepsOldImageAndDeletesNewOne_whenSaveFailsAfterNewUpload() {
        Pizza existing = TestDataFactory.pizza("Old Name", new BigDecimal("10.00"), "Classic", true);
        existing.setId(7L);
        existing.setImagePublicId("pizzas/old-1");
        when(pizzaRepository.findById(7L)).thenReturn(Optional.of(existing));

        PizzaDTO dto = dto("New Name", "Classic", "12.00", true);
        MockMultipartFile newImage = new MockMultipartFile("image", "new.png", "image/png", new byte[]{9, 9});
        UploadResult newUpload = new UploadResult("https://cdn.test/new.png", "pizzas/new-1");
        when(cloudinaryService.upload(newImage)).thenReturn(newUpload);
        when(pizzaRepository.saveAndFlush(existing)).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> pizzaService.update(7L, dto, newImage));

        verify(cloudinaryService).delete("pizzas/new-1");
        verify(cloudinaryService, never()).delete("pizzas/old-1");
    }

    // ---------------------------------------------------------------- delete (characterization)

    @Test
    void delete_callsCloudinaryDeleteBeforeRepositoryDelete_characterizesKnownOrderingBug() {
        // KNOWN, CONFIRMED BUG (not fixed here, intentionally): PizzaService.delete()
        // deletes the Cloudinary image BEFORE deleting the DB row. If the
        // repository delete then fails, the pizza row survives pointing at an
        // image that no longer exists. This test locks in the CURRENT call
        // order so any accidental reordering is caught, not to endorse it.
        Pizza pizza = TestDataFactory.pizza();
        pizza.setId(3L);
        pizza.setImagePublicId("pizzas/to-delete");
        when(pizzaRepository.findById(3L)).thenReturn(Optional.of(pizza));

        pizzaService.delete(3L);

        InOrder order = inOrder(cloudinaryService, pizzaRepository);
        order.verify(cloudinaryService).delete("pizzas/to-delete");
        order.verify(pizzaRepository).delete(pizza);
    }

    // ---------------------------------------------------------------- findById

    @Test
    void findById_throwsResourceNotFoundException_whenMissing() {
        when(pizzaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pizzaService.findById(99L));
    }
}
