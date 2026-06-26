package com.pizza.controller;

import com.pizza.dto.PizzaDTO;
import com.pizza.entity.Pizza;
import com.pizza.service.PizzaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin-only pizza management (US-004 add, US-005 update, US-006 delete) plus a
 * management list. Every route here lives under {@code /admin/pizzas/**} and is
 * protected by {@code AdminAuthInterceptor}, so customers can never reach it.
 */
@Controller
@RequestMapping("/admin/pizzas")
@RequiredArgsConstructor
public class AdminPizzaController {

    private final PizzaService pizzaService;

    /** Admin management list with search and category filter. */
    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            Model model) {
        List<Pizza> pizzas = pizzaService.search(search, category, sort);
        model.addAttribute("pizzas", pizzas);
        model.addAttribute("categories", pizzaService.findCategories());
        model.addAttribute("search", search);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("sort", sort);
        return "admin-pizza-list";
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        if (!model.containsAttribute("pizzaDTO")) {
            model.addAttribute("pizzaDTO", new PizzaDTO());
        }
        return "add-pizza";
    }

    /** US-004: add a pizza (image required). */
    @PostMapping("/add")
    public String add(
            @Valid @ModelAttribute("pizzaDTO") PizzaDTO pizzaDTO,
            BindingResult bindingResult,
            @RequestParam("image") MultipartFile image,
            RedirectAttributes redirectAttributes) {

        if (image == null || image.isEmpty()) {
            bindingResult.rejectValue("imageUrl", "image.required", "An image is required");
        }
        if (bindingResult.hasErrors()) {
            return "add-pizza";
        }

        Pizza saved = pizzaService.add(pizzaDTO, image);
        redirectAttributes.addFlashAttribute("successMessage",
                "Pizza \"" + saved.getName() + "\" added successfully.");
        return "redirect:/admin/pizzas";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("pizzaDTO")) {
            model.addAttribute("pizzaDTO", pizzaService.toDto(pizzaService.findById(id)));
        }
        model.addAttribute("pizzaId", id);
        return "edit-pizza";
    }

    /** US-005: update a pizza (image optional; old image replaced if changed). */
    @PostMapping("/edit/{id}")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("pizzaDTO") PizzaDTO pizzaDTO,
            BindingResult bindingResult,
            @RequestParam(value = "image", required = false) MultipartFile image,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("pizzaId", id);
            return "edit-pizza";
        }

        Pizza saved = pizzaService.update(id, pizzaDTO, image);
        redirectAttributes.addFlashAttribute("successMessage",
                "Pizza \"" + saved.getName() + "\" updated successfully.");
        return "redirect:/admin/pizzas";
    }

    /** US-006: delete a pizza and its Cloudinary image. */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        pizzaService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Pizza deleted successfully.");
        return "redirect:/admin/pizzas";
    }
}
