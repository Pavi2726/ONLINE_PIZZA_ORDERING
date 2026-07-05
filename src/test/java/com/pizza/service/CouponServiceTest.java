package com.pizza.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pizza.dto.CouponDTO;
import com.pizza.entity.Coupon;
import com.pizza.repository.CouponRepository;
import com.pizza.testsupport.TestDataFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link CouponService}.
 *
 * <p>Note on exception types (confirmed from source): {@code createCoupon}'s
 * duplicate-code guard throws {@link IllegalArgumentException}, while
 * {@code getCouponById}/{@code updateCoupon}/{@code validateCoupon}'s
 * not-found/invalid-code guards throw a raw {@link RuntimeException} - the
 * class does not use {@code ResourceNotFoundException} anywhere.</p>
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponService couponService;

    private CouponDTO dto(String code, int discountPercentage, boolean active) {
        CouponDTO dto = new CouponDTO();
        dto.setCouponCode(code);
        dto.setDiscountPercentage(discountPercentage);
        dto.setActive(active);
        return dto;
    }

    // ---------------------------------------------------------------- createCoupon

    @Test
    void createCoupon_duplicateCode_throwsIllegalArgumentException() {
        when(couponRepository.existsByCouponCode("SAVE10")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(dto("save10", 10, true)));

        assertThat(ex.getMessage()).isEqualTo("Coupon code already exists.");
        verify(couponRepository, never()).save(any());
    }

    @Test
    void createCoupon_newCode_normalizesCodeAndSaves() {
        when(couponRepository.existsByCouponCode("SAVE10")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        Coupon result = couponService.createCoupon(dto("  save10  ", 10, true));

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        Coupon saved = captor.getValue();

        assertThat(saved.getCouponCode()).isEqualTo("SAVE10");
        assertThat(saved.getDiscountPercentage()).isEqualTo(10);
        assertThat(saved.isActive()).isTrue();
        assertThat(result).isSameAs(saved);
    }

    // ---------------------------------------------------------------- getCouponById

    @Test
    void getCouponById_found_returnsCoupon() {
        Coupon coupon = TestDataFactory.coupon();
        coupon.setId(5L);
        when(couponRepository.findById(5L)).thenReturn(Optional.of(coupon));

        assertThat(couponService.getCouponById(5L)).isSameAs(coupon);
    }

    @Test
    void getCouponById_notFound_throwsRuntimeException() {
        when(couponRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> couponService.getCouponById(99L));

        assertThat(ex.getClass()).isEqualTo(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Coupon not found");
    }

    // ---------------------------------------------------------------- updateCoupon

    @Test
    void updateCoupon_found_updatesFieldsAndSaves() {
        Coupon existing = TestDataFactory.coupon(10, true);
        existing.setId(3L);
        when(couponRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        Coupon result = couponService.updateCoupon(3L, dto(" newcode ", 25, false));

        assertThat(result.getCouponCode()).isEqualTo("NEWCODE");
        assertThat(result.getDiscountPercentage()).isEqualTo(25);
        assertThat(result.isActive()).isFalse();
        verify(couponRepository).save(existing);
    }

    @Test
    void updateCoupon_notFound_throwsRuntimeException() {
        when(couponRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> couponService.updateCoupon(42L, dto("ANY", 10, true)));
        verify(couponRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- deleteCoupon

    @Test
    void deleteCoupon_found_deletesIt() {
        Coupon existing = TestDataFactory.coupon();
        existing.setId(7L);
        when(couponRepository.findById(7L)).thenReturn(Optional.of(existing));

        couponService.deleteCoupon(7L);

        verify(couponRepository, times(1)).delete(existing);
    }

    @Test
    void deleteCoupon_notFound_throwsRuntimeExceptionAndDoesNotDelete() {
        when(couponRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> couponService.deleteCoupon(404L));
        verify(couponRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------- validateCoupon

    @Test
    void validateCoupon_unknownCode_throwsRuntimeException() {
        when(couponRepository.findByCouponCode("MISSING")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> couponService.validateCoupon("missing"));

        assertThat(ex.getClass()).isEqualTo(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Invalid coupon code.");
    }

    @Test
    void validateCoupon_inactiveCode_throwsRuntimeException() {
        Coupon inactive = TestDataFactory.coupon(15, false);
        inactive.setCouponCode("STALE");
        when(couponRepository.findByCouponCode("STALE")).thenReturn(Optional.of(inactive));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> couponService.validateCoupon("stale"));

        assertThat(ex.getMessage()).isEqualTo("Coupon is inactive.");
    }

    @Test
    void validateCoupon_activeCode_returnsCoupon() {
        Coupon active = TestDataFactory.coupon(20, true);
        active.setCouponCode("FRESH");
        when(couponRepository.findByCouponCode("FRESH")).thenReturn(Optional.of(active));

        Coupon result = couponService.validateCoupon(" fresh ");

        assertThat(result).isSameAs(active);
    }
}
