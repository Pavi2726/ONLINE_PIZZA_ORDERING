package com.pizza.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pizza.dto.CustomerUpdateDTO;
import com.pizza.entity.Customer;
import com.pizza.service.AdminCustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;

    // List All Customers, with optional search/sort (US-015)
    @GetMapping
    public String list(@RequestParam(required = false) String search,
                        @RequestParam(required = false) String sort,
                        Model model) {
        List<Customer> customers = adminCustomerService.search(search, sort);
        model.addAttribute("customers", customers);
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        return "admin-customer-list";
    }

    // Show Edit Customer Form
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Customer customer = adminCustomerService.getById(id);

        CustomerUpdateDTO dto = new CustomerUpdateDTO();
        dto.setFirstName(customer.getFirstName());
        dto.setLastName(customer.getLastName());
        dto.setEmail(customer.getEmail());
        dto.setPhone(customer.getPhone());
        dto.setAddress(customer.getAddress());

        model.addAttribute("customerDTO", dto);
        model.addAttribute("customerId", id);

        return "edit-customer";
    }

    // Update Customer
    @PostMapping("/update/{id}")
    public String updateCustomer(
            @PathVariable Long id,
            @Valid @ModelAttribute("customerDTO") CustomerUpdateDTO customerDTO,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("customerId", id);
            return "edit-customer";
        }

        try {
            Customer customer = adminCustomerService.updateCustomer(id, customerDTO);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Customer \"" + customer.getFullName() + "\" updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/customers";
    }
}
