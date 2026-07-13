package com.pizza.api;

import com.pizza.api.dto.ApiResponses.Envelope;
import com.pizza.api.dto.ApiResponses.PizzaListResponse;
import com.pizza.api.dto.ApiResponses.PizzaResponse;
import com.pizza.dto.PizzaDTO;
import com.pizza.entity.Pizza;
import com.pizza.service.PizzaService;
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
 * Admin pizza management, including the Cloudinary image upload.
 *
 * <p>Create/update take {@code multipart/form-data} via {@code @ModelAttribute} rather
 * than a JSON body plus a file part: the client can then send a plain {@code FormData},
 * and the existing {@code PizzaDTO} bean validation applies unchanged (a failure raises
 * BindException, which the API exception handler renders as field errors). Update is a
 * POST, not a PUT — multipart on PUT is not reliably parsed by the servlet container.</p>
 */
@RestController
@RequestMapping("/api/admin/pizzas")
@RequiredArgsConstructor
public class AdminPizzaApiController {

    private final PizzaService pizzaService;

    @GetMapping
    public PizzaListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort) {
        return new PizzaListResponse(
                ApiMappers.pizzas(pizzaService.search(search, category, sort)),
                pizzaService.findCategories());
    }

    @GetMapping("/{id}")
    public PizzaResponse get(@PathVariable Long id) {
        return ApiMappers.pizza(pizzaService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Envelope<PizzaResponse>> add(
            @Valid @ModelAttribute PizzaDTO pizzaDTO,
            BindingResult bindingResult,
            @RequestParam(value = "image", required = false) MultipartFile image) throws BindException {

        if (image == null || image.isEmpty()) {
            bindingResult.rejectValue("imageUrl", "image.required", "An image is required");
        }
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }

        Pizza saved = pizzaService.add(pizzaDTO, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(Envelope.success(
                "Pizza \"" + saved.getName() + "\" added successfully.", ApiMappers.pizza(saved)));
    }

    /** Image is optional here; omitting it keeps the existing Cloudinary asset. */
    @PostMapping("/{id}")
    public Envelope<PizzaResponse> update(
            @PathVariable Long id,
            @Valid @ModelAttribute PizzaDTO pizzaDTO,
            BindingResult bindingResult,
            @RequestParam(value = "image", required = false) MultipartFile image) throws BindException {

        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }

        Pizza saved = pizzaService.update(id, pizzaDTO, image);
        return Envelope.success(
                "Pizza \"" + saved.getName() + "\" updated successfully.", ApiMappers.pizza(saved));
    }

    @DeleteMapping("/{id}")
    public Envelope<Void> delete(@PathVariable Long id) {
        pizzaService.delete(id);
        return Envelope.success("Pizza deleted successfully.", null);
    }
}
