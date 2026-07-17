package com.pizza.service;

import com.pizza.dto.PizzaDTO;
import com.pizza.entity.OrderStatus;
import com.pizza.entity.Pizza;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.CartItemRepository;
import com.pizza.repository.OrderItemRepository;
import com.pizza.repository.PizzaRepository;
import com.pizza.service.CloudinaryService.UploadResult;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Business logic for the pizza catalogue (US-003 to US-006): browsing,
 * searching, sorting and admin add/update/delete with Cloudinary image
 * management.
 */
@Service
@RequiredArgsConstructor
public class PizzaService {

    private static final Logger log = LoggerFactory.getLogger(PizzaService.class);

    private final PizzaRepository pizzaRepository;
    private final CloudinaryService cloudinaryService;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;

    /** Returns all pizzas. */
    @Transactional(readOnly = true)
    public List<Pizza> findAll() {
        return pizzaRepository.findAll();
    }

    /** Total number of pizzas (admin dashboard stat). */
    @Transactional(readOnly = true)
    public long countAll() {
        return pizzaRepository.count();
    }

    /** Number of available pizzas (admin dashboard stat). */
    @Transactional(readOnly = true)
    public long countAvailable() {
        return pizzaRepository.findByAvailableTrue().size();
    }

    /** Number of out-of-stock pizzas (admin dashboard stat). */
    @Transactional(readOnly = true)
    public long countOutOfStock() {
        return countAll() - countAvailable();
    }

    /**
     * Catalogue query supporting search, category filter and price sort
     * (US-003).
     *
     * @param search   optional name fragment
     * @param category optional exact category ("" or null = all)
     * @param sort     "priceAsc", "priceDesc" or null
     */
    @Transactional(readOnly = true)
    public List<Pizza> search(String search, String category, String sort) {
        boolean hasSearch = StringUtils.hasText(search);
        boolean hasCategory = StringUtils.hasText(category);

        List<Pizza> results;
        if (hasSearch && hasCategory) {
            results = pizzaRepository.findByCategoryAndNameContainingIgnoreCase(category, search);
        } else if (hasCategory) {
            results = pizzaRepository.findByCategory(category);
        } else if (hasSearch) {
            results = pizzaRepository.findByNameContainingIgnoreCase(search);
        } else {
            results = pizzaRepository.findAll();
        }

        if ("priceAsc".equals(sort)) {
            results.sort(Comparator.comparing(Pizza::getPrice));
        } else if ("priceDesc".equals(sort)) {
            results.sort(Comparator.comparing(Pizza::getPrice).reversed());
        }
        return results;
    }

    /** Distinct categories for filter dropdowns. */
    @Transactional(readOnly = true)
    public List<String> findCategories() {
        return pizzaRepository.findAll().stream()
                .map(Pizza::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    /** Finds a pizza by id or throws {@link ResourceNotFoundException}. */
    @Transactional(readOnly = true)
    public Pizza findById(Long id) {
        return pizzaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pizza not found: id " + id));
    }

    /**
     * Adds a new pizza, uploading its image to Cloudinary (US-004).
     *
     * @param dto   the validated pizza form
     * @param image the required image file
     * @return the persisted pizza
     */
    @Transactional
    public Pizza add(PizzaDTO dto, MultipartFile image) {
        // Upload first; if the DB save fails we delete it to avoid an orphan.
        UploadResult upload = cloudinaryService.upload(image);
        try {
            Pizza pizza = Pizza.builder()
                    .name(dto.getName().trim())
                    .description(dto.getDescription().trim())
                    .category(dto.getCategory().trim())
                    .price(dto.getPrice())
                    .imageUrl(upload.secureUrl())
                    .imagePublicId(upload.publicId())
                    .available(dto.isAvailable())
                    .build();
            return pizzaRepository.saveAndFlush(pizza);
        } catch (RuntimeException ex) {
            cloudinaryService.delete(upload.publicId());
            throw ex;
        }
    }

    /**
     * Updates an existing pizza, optionally replacing its Cloudinary image
     * (US-005). When a new image is provided, the old one is deleted.
     *
     * @param id    the pizza id
     * @param dto   the validated pizza form
     * @param image the optional replacement image (may be null/empty)
     * @return the updated pizza
     */
    @Transactional
    public Pizza update(Long id, PizzaDTO dto, MultipartFile image) {
        Pizza pizza = findById(id);
        String oldPublicId = pizza.getImagePublicId();

        pizza.setName(dto.getName().trim());
        pizza.setDescription(dto.getDescription().trim());
        pizza.setCategory(dto.getCategory().trim());
        pizza.setPrice(dto.getPrice());
        pizza.setAvailable(dto.isAvailable());

        // Upload the new image first but DON'T delete the old one yet, so a
        // failed DB save never leaves us without a valid image.
        UploadResult newUpload = null;
        if (image != null && !image.isEmpty()) {
            newUpload = cloudinaryService.upload(image);
            pizza.setImageUrl(newUpload.secureUrl());
            pizza.setImagePublicId(newUpload.publicId());
        }

        try {
            Pizza saved = pizzaRepository.saveAndFlush(pizza);
            // Save succeeded: now it is safe to remove the replaced old image.
            if (newUpload != null && oldPublicId != null && !oldPublicId.isBlank()) {
                cloudinaryService.delete(oldPublicId);
            }
            return saved;
        } catch (RuntimeException ex) {
            // Save failed: remove the freshly uploaded image, keep the old one.
            if (newUpload != null) {
                cloudinaryService.delete(newUpload.publicId());
            }
            throw ex;
        }
    }

    /**
     * Deletes a pizza and its Cloudinary image (US-006).
     *
     * <p>Blocked only while the pizza is on an order that is still active
     * (not {@code DELIVERED}/{@code CANCELLED}). Once every referencing
     * order is terminal, the delete proceeds: any still-open shopping
     * carts holding this pizza are cleared first, then historical order
     * items are detached (their {@code pizza} reference is nulled -
     * {@code price}/{@code quantity}/{@code lineTotal} already live on the
     * order item itself, so past orders keep their totals and render the
     * item as unavailable rather than losing the row).
     *
     * <p>The Cloudinary image cleanup is best-effort: a Cloudinary failure
     * (misconfiguration, the CDN being down, a flaky response) is logged
     * and swallowed rather than rolling back the pizza deletion - an
     * orphaned CDN image is a minor, recoverable issue, whereas an admin
     * being unable to remove a pizza from the menu is not.
     *
     * @param id the pizza id
     */
    @Transactional
    public void delete(Long id) {
        Pizza pizza = findById(id);
        String publicId = pizza.getImagePublicId();

        boolean hasActiveOrder = orderItemRepository.existsByPizza_IdAndOrder_StatusNotIn(
                id, List.of(OrderStatus.DELIVERED.name(), OrderStatus.CANCELLED.name()));
        if (hasActiveOrder) {
            throw new IllegalStateException(
                    "This pizza cannot be deleted because it is part of an order that has not been delivered or cancelled yet.");
        }

        cartItemRepository.deleteByPizzaId(id);
        orderItemRepository.clearPizzaReferences(id);

        pizzaRepository.delete(pizza);
        pizzaRepository.flush();

        try {
            cloudinaryService.delete(publicId);
        } catch (RuntimeException ex) {
            log.warn("Pizza {} was deleted, but its Cloudinary image ({}) could not be removed", id, publicId, ex);
        }
    }

    /** Maps an entity to a form-backing DTO (for the edit screen). */
    public PizzaDTO toDto(Pizza pizza) {
        PizzaDTO dto = new PizzaDTO();
        dto.setId(pizza.getId());
        dto.setName(pizza.getName());
        dto.setDescription(pizza.getDescription());
        dto.setCategory(pizza.getCategory());
        dto.setPrice(pizza.getPrice());
        dto.setImageUrl(pizza.getImageUrl());
        dto.setAvailable(pizza.isAvailable());
        return dto;
    }
}
