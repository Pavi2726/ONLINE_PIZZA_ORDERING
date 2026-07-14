package com.pizza.api;

import com.pizza.api.dto.ApiResponses.DrinkListResponse;
import com.pizza.api.dto.ApiResponses.DrinkResponse;
import com.pizza.api.dto.ApiResponses.Envelope;
import com.pizza.dto.DrinkDTO;
import com.pizza.entity.Drink;
import com.pizza.service.DrinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin drink management, including Cloudinary image upload.
 *
 * <p>Create/update take {@code multipart/form-data} via {@code @ModelAttribute}.
 * Update is a POST (not PUT) — multipart on PUT is not reliably parsed.</p>
 */
@RestController
@RequestMapping("/api/admin/drinks")
@RequiredArgsConstructor
public class AdminDrinkApiController {

    private final DrinkService drinkService;

    @GetMapping
    public DrinkListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort) {
        return new DrinkListResponse(
                ApiMappers.drinks(drinkService.search(search, category, sort)),
                DrinkService.PREDEFINED_CATEGORIES);
    }

    @GetMapping("/{id}")
    public DrinkResponse get(@PathVariable Long id) {
        return ApiMappers.drink(drinkService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Envelope<DrinkResponse>> add(
            @Valid @ModelAttribute DrinkDTO drinkDTO,
            BindingResult bindingResult,
            @RequestParam(value = "image", required = false) MultipartFile image) throws BindException {

        if (image == null || image.isEmpty()) {
            bindingResult.rejectValue("imageUrl", "image.required", "An image is required");
        }
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }

        Drink saved = drinkService.add(drinkDTO, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(Envelope.success(
                "Drink \"" + saved.getName() + "\" added successfully.", ApiMappers.drink(saved)));
    }

    /** Image is optional here; omitting it keeps the existing Cloudinary asset. */
    @PostMapping("/{id}")
    public Envelope<DrinkResponse> update(
            @PathVariable Long id,
            @Valid @ModelAttribute DrinkDTO drinkDTO,
            BindingResult bindingResult,
            @RequestParam(value = "image", required = false) MultipartFile image) throws BindException {

        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }

        Drink saved = drinkService.update(id, drinkDTO, image);
        return Envelope.success(
                "Drink \"" + saved.getName() + "\" updated successfully.", ApiMappers.drink(saved));
    }

    @DeleteMapping("/{id}")
    public Envelope<Void> delete(@PathVariable Long id) {
        drinkService.delete(id);
        return Envelope.success("Drink deleted successfully.", null);
    }
}
