package com.pizza.api;

import com.pizza.api.dto.ApiResponses.CouponResponse;
import com.pizza.api.dto.ApiResponses.Envelope;
import com.pizza.dto.CouponDTO;
import com.pizza.entity.Coupon;
import com.pizza.service.CouponService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin coupon management. */
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponApiController {

    private final CouponService couponService;

    @GetMapping
    public List<CouponResponse> list() {
        return ApiMappers.coupons(couponService.findAll());
    }

    @GetMapping("/{id}")
    public CouponResponse get(@PathVariable Long id) {
        return ApiMappers.coupon(couponService.getCouponById(id));
    }

    @PostMapping
    public ResponseEntity<Envelope<CouponResponse>> create(@Valid @RequestBody CouponDTO dto) {
        Coupon coupon = couponService.createCoupon(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Envelope.success(
                "Coupon \"" + coupon.getCouponCode() + "\" created successfully.",
                ApiMappers.coupon(coupon)));
    }

    @PutMapping("/{id}")
    public Envelope<CouponResponse> update(@PathVariable Long id, @Valid @RequestBody CouponDTO dto) {
        Coupon coupon = couponService.updateCoupon(id, dto);
        return Envelope.success(
                "Coupon \"" + coupon.getCouponCode() + "\" updated successfully.",
                ApiMappers.coupon(coupon));
    }

    @DeleteMapping("/{id}")
    public Envelope<Void> delete(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return Envelope.success("Coupon deleted successfully.", null);
    }
}
