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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pizza.dto.CouponDTO;
import com.pizza.entity.Coupon;
import com.pizza.service.CouponService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    // Show Add Coupon Form
    @GetMapping("/add")
    public String showAddForm(Model model) {
        if (!model.containsAttribute("couponDTO")) {
            model.addAttribute("couponDTO", new CouponDTO());
        }
        return "add-coupon";
    }

    // List All Coupons
    @GetMapping
    public String list(Model model) {
        List<Coupon> coupons = couponService.findAll();
        model.addAttribute("coupons", coupons);
        return "admin-coupon-list";
    }

    // Save New Coupon
    @PostMapping("/add")
    public String addCoupon(
            @Valid @ModelAttribute("couponDTO") CouponDTO couponDTO,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "add-coupon";
        }

        Coupon coupon = couponService.createCoupon(couponDTO);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Coupon \"" + coupon.getCouponCode() + "\" created successfully."
        );

        return "redirect:/admin/coupons";
    }

    // Show Edit Coupon Form
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {

        Coupon coupon = couponService.getCouponById(id);

        CouponDTO couponDTO = new CouponDTO();
        couponDTO.setCouponCode(coupon.getCouponCode());
        couponDTO.setDiscountPercentage(coupon.getDiscountPercentage());
        couponDTO.setActive(coupon.isActive());

        model.addAttribute("couponDTO", couponDTO);
        model.addAttribute("couponId", id);

        return "edit-coupon";
    }

    // Update Coupon
    @PostMapping("/update/{id}")
    public String updateCoupon(
            @PathVariable Long id,
            @Valid @ModelAttribute("couponDTO") CouponDTO couponDTO,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "edit-coupon";
        }

        try {
            Coupon coupon = couponService.updateCoupon(id, couponDTO);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Coupon \"" + coupon.getCouponCode() + "\" updated successfully."
            );

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    e.getMessage()
            );
        }

        return "redirect:/admin/coupons";
    }

    @PostMapping("/delete/{id}")
public String deleteCoupon(@PathVariable Long id,
                           RedirectAttributes redirectAttributes) {

    couponService.deleteCoupon(id);

    redirectAttributes.addFlashAttribute(
            "successMessage",
            "Coupon deleted successfully."
    );

    return "redirect:/admin/coupons";
}
}