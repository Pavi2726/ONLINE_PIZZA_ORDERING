package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Coupon;
import com.pizza.repository.CouponRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of admin coupon management against real H2 rows.
 *
 * <p>Duplicate coupon codes raise {@code IllegalArgumentException} in
 * {@link com.pizza.service.CouponService}, which the API maps to a 400 with the real
 * message - on create and on update alike. The server-rendered app rendered an error
 * page for one and a flash redirect for the other; the API is consistent.
 */
class AdminCouponManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private EntityManager entityManager;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    @Test
    void createCoupon_withValidData_persistsRealRowInH2() throws Exception {
        String newCode = TestDataFactory.coupon().getCouponCode();

        mockMvc.perform(post("/api/admin/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "couponCode", newCode,
                                "discountPercentage", 25,
                                "active", true)))
                        .session(adminSession()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Coupon \"" + newCode + "\" created successfully."));

        Coupon saved = couponRepository.findByCouponCode(newCode).orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDiscountPercentage()).isEqualTo(25);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void createCoupon_withDuplicateCode_isRejected_andNoSecondRowPersisted() throws Exception {
        Coupon existing = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));

        mockMvc.perform(post("/api/admin/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "couponCode", existing.getCouponCode(),
                                "discountPercentage", 50,
                                "active", true)))
                        .session(adminSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Coupon code already exists."));

        List<Coupon> matches = couponRepository.findAll().stream()
                .filter(c -> c.getCouponCode().equals(existing.getCouponCode()))
                .toList();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getDiscountPercentage()).isEqualTo(10);
    }

    /** Bean validation on CouponDTO is enforced through the JSON body. */
    @Test
    void createCoupon_withOutOfRangeDiscount_isRejectedWithAFieldError() throws Exception {
        mockMvc.perform(post("/api/admin/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "couponCode", "TOOBIG",
                                "discountPercentage", 150,
                                "active", true)))
                        .session(adminSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.discountPercentage").value("Discount cannot exceed 100%"));
    }

    @Test
    void list_reflectsRealSeededCoupons() throws Exception {
        Coupon coupon1 = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        Coupon coupon2 = couponRepository.saveAndFlush(TestDataFactory.coupon(30, false));

        String body = mockMvc.perform(get("/api/admin/coupons").session(adminSession()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains(coupon1.getCouponCode());
        assertThat(body).contains(coupon2.getCouponCode());
    }

    @Test
    void getCoupon_returnsRealPersistedValues() throws Exception {
        Coupon coupon = couponRepository.saveAndFlush(TestDataFactory.coupon(40, true));

        mockMvc.perform(get("/api/admin/coupons/{id}", coupon.getId()).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponCode").value(coupon.getCouponCode()))
                .andExpect(jsonPath("$.discountPercentage").value(40))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateCoupon_withValidNonCollidingCode_persistsRealChangesInH2() throws Exception {
        Coupon coupon = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        Long id = coupon.getId();
        String newCode = TestDataFactory.coupon().getCouponCode();

        mockMvc.perform(put("/api/admin/coupons/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "couponCode", newCode,
                                "discountPercentage", 60,
                                "active", false)))
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Coupon \"" + newCode + "\" updated successfully."));

        entityManager.flush();
        entityManager.clear();

        Coupon reloaded = couponRepository.findById(id).orElseThrow();
        assertThat(reloaded.getCouponCode()).isEqualTo(newCode);
        assertThat(reloaded.getDiscountPercentage()).isEqualTo(60);
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    void updateCoupon_duplicateCode_isRejected_andLeavesRowUnchanged() throws Exception {
        Coupon coupon1 = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        Coupon coupon2 = couponRepository.saveAndFlush(TestDataFactory.coupon(20, true));
        Long coupon1Id = coupon1.getId();
        String coupon1OriginalCode = coupon1.getCouponCode();
        String coupon2Code = coupon2.getCouponCode();

        mockMvc.perform(put("/api/admin/coupons/{id}", coupon1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "couponCode", coupon2Code,
                                "discountPercentage", 99,
                                "active", true)))
                        .session(adminSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Coupon code already exists."));

        entityManager.flush();
        entityManager.clear();

        Optional<Coupon> reloaded = couponRepository.findById(coupon1Id);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getCouponCode()).isEqualTo(coupon1OriginalCode);
        assertThat(reloaded.get().getDiscountPercentage()).isEqualTo(10);
    }

    @Test
    void deleteCoupon_removesRealRowFromH2() throws Exception {
        Coupon coupon = couponRepository.saveAndFlush(TestDataFactory.coupon());
        Long id = coupon.getId();

        mockMvc.perform(delete("/api/admin/coupons/{id}", id).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Coupon deleted successfully."));

        assertThat(couponRepository.findById(id)).isEmpty();
    }
}
