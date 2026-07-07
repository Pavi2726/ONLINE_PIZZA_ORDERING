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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of admin pizza management (US-004/US-005/US-006):
 * real {@code MockMvc} calls hit the real {@link com.pizza.controller.AdminPizzaController},
 * real {@link com.pizza.service.PizzaService} and the real {@link PizzaRepository}
 * (including its derived-query methods) backed by H2. Only {@code CloudinaryService}
 * is mocked (inherited from {@link AbstractIntegrationTest}), so every upload/delete
 * call is stubbed/verified rather than hitting the network.
 *
 * <p><b>Scope note:</b> this class covers happy-path add/update/delete only. The
 * Cloudinary-delete-ordering bug (deleting a pizza that a placed order still
 * references) is deliberately NOT characterized here - that is a separate task's
 * dedicated test.
 */
class PizzaCatalogueIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PizzaRepository pizzaRepository;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    // ------------------------------------------------------------------ add

    @Test
    void add_withValidDataAndImage_persistsPizzaWithUploadedImageDetails() throws Exception {
        UploadResult upload = new UploadResult(
                "https://cloudinary.test/pizza-ordering/pizzas/new-margherita.jpg",
                "pizza-ordering/pizzas/new-margherita");
        when(cloudinaryService.upload(any())).thenReturn(upload);

        MockMultipartFile image = new MockMultipartFile("image", "pizza.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/admin/pizzas/add")
                        .file(image)
                        .param("name", "Fresh Margherita")
                        .param("description", "Tomato, mozzarella and basil")
                        .param("category", "Classic")
                        .param("price", "12.50")
                        .param("available", "true")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/pizzas"))
                .andExpect(flash().attribute("successMessage",
                        "Pizza \"Fresh Margherita\" added successfully."));

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

    // --------------------------------------------------------------- update

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

        mockMvc.perform(multipart("/admin/pizzas/edit/" + existing.getId())
                        .file(image)
                        .param("name", "Updated Name")
                        .param("description", "Updated description")
                        .param("category", "Specialty")
                        .param("price", "15.00")
                        .param("available", "false")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/pizzas"))
                .andExpect(flash().attribute("successMessage",
                        "Pizza \"Updated Name\" updated successfully."));

        Pizza updated = pizzaRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getCategory()).isEqualTo("Specialty");
        assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(updated.isAvailable()).isFalse();
        assertThat(updated.getImageUrl()).isEqualTo(newUpload.secureUrl());
        assertThat(updated.getImagePublicId()).isEqualTo(newUpload.publicId());

        // The old image is only deleted after the DB save succeeded.
        verify(cloudinaryService).delete(oldPublicId);
    }

    // --------------------------------------------------------------- delete

    @Test
    void delete_withNoReferencingOrders_removesPizzaFromH2AndDeletesItsImage() throws Exception {
        Pizza pizza = pizzaRepository.saveAndFlush(TestDataFactory.pizza());
        Long id = pizza.getId();
        String publicId = pizza.getImagePublicId();

        mockMvc.perform(post("/admin/pizzas/delete/" + id).session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/pizzas"))
                .andExpect(flash().attribute("successMessage", "Pizza deleted successfully."));

        Optional<Pizza> afterDelete = pizzaRepository.findById(id);
        assertThat(afterDelete).isEmpty();
        verify(cloudinaryService).delete(publicId);
    }

    // ----------------------------------------------------- derived queries

    @Test
    void adminList_withSearchAndCategoryFilter_usesRealDerivedQueryAgainstH2() throws Exception {
        pizzaRepository.saveAndFlush(TestDataFactory.pizza("Veggie Delight", new BigDecimal("9.00"), "Vegetarian", true));
        pizzaRepository.saveAndFlush(TestDataFactory.pizza("Pepperoni Feast", new BigDecimal("11.00"), "Meat", true));
        pizzaRepository.saveAndFlush(TestDataFactory.pizza("Veggie Supreme", new BigDecimal("10.00"), "Meat", true));

        mockMvc.perform(get("/admin/pizzas")
                        .param("search", "Veggie")
                        .param("category", "Meat")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pizzas", Matchers.hasSize(1)))
                .andExpect(model().attribute("pizzas", Matchers.contains(
                        Matchers.hasProperty("name", Matchers.is("Veggie Supreme")))));
    }
}
