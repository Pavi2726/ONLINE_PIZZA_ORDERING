package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Pizza;
import com.pizza.repository.PizzaRepository;
import com.pizza.service.CloudinaryService.UploadResult;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of admin pizza management (US-004/005/006), including the
 * multipart image upload. The create/update endpoints take {@code multipart/form-data}
 * and bind it onto the existing {@code PizzaDTO}, so the request shape below is the same
 * one the browser sends as a {@code FormData}.
 */
class PizzaCatalogueIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PizzaRepository pizzaRepository;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    @Test
    void add_withValidDataAndImage_persistsPizzaWithUploadedImageDetails() throws Exception {
        UploadResult upload = new UploadResult(
                "https://cloudinary.test/pizza-ordering/pizzas/new-margherita.jpg",
                "pizza-ordering/pizzas/new-margherita");
        when(cloudinaryService.upload(any())).thenReturn(upload);

        MockMultipartFile image = new MockMultipartFile("image", "pizza.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/admin/pizzas")
                        .file(image)
                        .param("name", "Fresh Margherita")
                        .param("description", "Tomato, mozzarella and basil")
                        .param("category", "Classic")
                        .param("price", "12.50")
                        .param("available", "true")
                        .session(adminSession()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Pizza \"Fresh Margherita\" added successfully."))
                .andExpect(jsonPath("$.data.imageUrl").value(upload.secureUrl()));

        List<Pizza> matches = pizzaRepository.findByNameContainingIgnoreCase("Fresh Margherita");
        assertThat(matches).hasSize(1);
        Pizza saved = matches.get(0);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDescription()).isEqualTo("Tomato, mozzarella and basil");
        assertThat(saved.getCategory()).isEqualTo("Classic");
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(saved.isAvailable()).isTrue();
        assertThat(saved.getImageUrl()).isEqualTo(upload.secureUrl());
        assertThat(saved.getImagePublicId()).isEqualTo(upload.publicId());
        verify(cloudinaryService).upload(any());
    }

    /** An image is mandatory on create, and the failure is reported against the form field. */
    @Test
    void add_withNoImage_isRejectedWithAFieldError_andPersistsNothing() throws Exception {
        mockMvc.perform(multipart("/api/admin/pizzas")
                        .param("name", "No Image Pizza")
                        .param("description", "Should not be saved")
                        .param("category", "Classic")
                        .param("price", "12.50")
                        .param("available", "true")
                        .session(adminSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.imageUrl").value("An image is required"));

        assertThat(pizzaRepository.findByNameContainingIgnoreCase("No Image Pizza")).isEmpty();
    }

    /** Bean validation on PizzaDTO still applies through the multipart binding. */
    @Test
    void add_withInvalidPrice_isRejectedWithAFieldError() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "pizza.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/admin/pizzas")
                        .file(image)
                        .param("name", "Free Pizza")
                        .param("description", "Priced at zero")
                        .param("category", "Classic")
                        .param("price", "0.00")
                        .param("available", "true")
                        .session(adminSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").value("Price must be greater than 0"));
    }

    @Test
    void update_withNewImage_updatesFieldsAndDeletesOldImageAfterSuccessfulSave() throws Exception {
        Pizza existing = pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Old Name", new BigDecimal("8.00"), "Classic", true));
        String oldPublicId = existing.getImagePublicId();

        UploadResult newUpload = new UploadResult(
                "https://cloudinary.test/pizza-ordering/pizzas/updated.jpg",
                "pizza-ordering/pizzas/updated");
        when(cloudinaryService.upload(any())).thenReturn(newUpload);

        MockMultipartFile image = new MockMultipartFile("image", "updated.png", "image/png", new byte[]{4, 5, 6});

        // POST, not PUT: multipart on PUT is not reliably parsed by the servlet container.
        mockMvc.perform(multipart("/api/admin/pizzas/{id}", existing.getId())
                        .file(image)
                        .param("name", "Updated Name")
                        .param("description", "Updated description")
                        .param("category", "Specialty")
                        .param("price", "15.00")
                        .param("available", "false")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pizza \"Updated Name\" updated successfully."));

        Pizza updated = pizzaRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getCategory()).isEqualTo("Specialty");
        assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(updated.isAvailable()).isFalse();
        assertThat(updated.getImageUrl()).isEqualTo(newUpload.secureUrl());
        assertThat(updated.getImagePublicId()).isEqualTo(newUpload.publicId());
        verify(cloudinaryService).delete(oldPublicId);
    }

    @Test
    void delete_withNoReferencingOrders_removesPizzaFromH2AndDeletesItsImage() throws Exception {
        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long id = pizza.getId();
        String publicId = pizza.getImagePublicId();

        mockMvc.perform(delete("/api/admin/pizzas/{id}", id).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pizza deleted successfully."));

        Optional<Pizza> afterDelete = pizzaRepository.findById(id);
        assertThat(afterDelete).isEmpty();
        verify(cloudinaryService).delete(publicId);
    }

    @Test
    void adminList_withSearchAndCategoryFilter_usesRealDerivedQueryAgainstH2() throws Exception {
        pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Veggie Delight", new BigDecimal("9.00"), "Vegetarian", true));
        pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Pepperoni Feast", new BigDecimal("11.00"), "Meat", true));
        pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Veggie Supreme", new BigDecimal("10.00"), "Meat", true));

        mockMvc.perform(get("/api/admin/pizzas")
                        .param("search", "Veggie")
                        .param("category", "Meat")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pizzas.length()").value(1))
                .andExpect(jsonPath("$.pizzas[0].name").value("Veggie Supreme"));
    }

    /** The public catalogue needs no session at all. */
    @Test
    void publicCatalogue_isReachableWithoutAnySession() throws Exception {
        pizzaRepository.saveAndFlush(
                TestDataFactory.pizza("Public Pizza", new BigDecimal("9.00"), "Classic", true));

        mockMvc.perform(get("/api/pizzas").param("search", "Public Pizza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pizzas.length()").value(1))
                .andExpect(jsonPath("$.pizzas[0].name").value("Public Pizza"));
    }
}
