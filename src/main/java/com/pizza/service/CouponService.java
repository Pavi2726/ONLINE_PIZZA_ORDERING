package com.pizza.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pizza.dto.CouponDTO;
import com.pizza.entity.Coupon;
import com.pizza.repository.CouponRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public Coupon createCoupon(CouponDTO dto) {

        if (couponRepository.existsByCouponCode(dto.getCouponCode().trim().toUpperCase())) {
            throw new IllegalArgumentException("Coupon code already exists.");
        }

        Coupon coupon = Coupon.builder()
                .couponCode(dto.getCouponCode().trim().toUpperCase())
                .discountPercentage(dto.getDiscountPercentage())
                .active(dto.isActive())
                .build();

        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public List<Coupon> findAll() {
        return couponRepository.findAll();
    }

    // Get Coupon by ID
    @Transactional(readOnly = true)
    public Coupon getCouponById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Coupon not found"));
    }

   // Update Coupon
    @Transactional
    public Coupon updateCoupon(Long id, CouponDTO dto) {

        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Coupon not found"));

        coupon.setCouponCode(dto.getCouponCode().trim().toUpperCase());
        coupon.setDiscountPercentage(dto.getDiscountPercentage());
        coupon.setActive(dto.isActive());

        return couponRepository.save(coupon);
    }

    @Transactional
    public void deleteCoupon(Long id) {

        Coupon coupon = getCouponById(id);

        couponRepository.delete(coupon);
    }
}