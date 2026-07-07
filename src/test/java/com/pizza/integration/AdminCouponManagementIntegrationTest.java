package com.pizza.integration;

import com.pizza.AbstractIntegrationTest;
import com.pizza.entity.Coupon;
import com.pizza.repository.CouponRepository;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end coverage of admin coupon management: real {@code MockMvc} calls
 * through the real {@link com.pizza.controller.AdminCouponController}, real
 * {@link com.pizza.service.CouponService} and the real {@link CouponRepository}
 * backed by H2.
 *
 * <p><b>Bug #9 fix (the main point of this class):</b> {@code
 * CouponService.updateCoupon} now checks {@code existsByCouponCodeAndIdNot}
 * before saving, mirroring {@code createCoupon}'s existing duplicate-code
 * guard - so renaming one coupon to another real coupon's code now throws a
 * clean {@code IllegalArgumentException} at the app level, before any SQL is
 * attempted, instead of relying on (and leaking) the database's raw unique
 * constraint violation. {@code AdminCouponController.updateCoupon}'s existing
 * try/catch surfaces this as a friendly flash message, same as {@code
 * createCoupon}'s equivalent case above.
 */
class AdminCouponManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private EntityManager entityManager;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    // ------------------------------------------------------------------ create

    @Test
    void createCoupon_withValidData_persistsRealRowInH2() throws Exception {
        String newCode = TestDataFactory.coupon().getCouponCode();

        mockMvc.perform(post("/admin/coupons/add")
                        .param("couponCode", newCode)
                        .param("discountPercentage", "25")
                        .param("active", "true")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attribute("successMessage",
                        "Coupon \"" + newCode + "\" created successfully."));

        Coupon saved = couponRepository.findByCouponCode(newCode).orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDiscountPercentage()).isEqualTo(25);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void createCoupon_withDuplicateCode_isRejectedAsBadRequestErrorPage_andNoSecondRowPersisted() throws Exception {
        Coupon existing = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));

        mockMvc.perform(post("/admin/coupons/add")
                        .param("couponCode", existing.getCouponCode())
                        .param("discountPercentage", "50")
                        .param("active", "true")
                        .session(adminSession()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("message", "Coupon code already exists."));

        List<Coupon> matches = couponRepository.findAll().stream()
                .filter(c -> c.getCouponCode().equals(existing.getCouponCode()))
                .toList();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getDiscountPercentage()).isEqualTo(10);
    }

    // -------------------------------------------------------------------- list

    @Test
    void list_reflectsRealSeededCoupons() throws Exception {
        Coupon coupon1 = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        Coupon coupon2 = couponRepository.saveAndFlush(TestDataFactory.coupon(30, false));

        mockMvc.perform(get("/admin/coupons").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-coupon-list"))
                .andExpect(model().attribute("coupons",
                        Matchers.hasItems(
                                Matchers.hasProperty("couponCode", Matchers.is(coupon1.getCouponCode())),
                                Matchers.hasProperty("couponCode", Matchers.is(coupon2.getCouponCode())))));
    }

    // -------------------------------------------------------------------- edit

    @Test
    void showEditForm_prefillsRealPersistedCouponValues() throws Exception {
        Coupon coupon = couponRepository.saveAndFlush(TestDataFactory.coupon(40, true));

        mockMvc.perform(get("/admin/coupons/edit/" + coupon.getId()).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("edit-coupon"))
                .andExpect(model().attribute("couponId", coupon.getId()))
                .andExpect(model().attribute("couponDTO",
                        Matchers.allOf(
                                Matchers.hasProperty("couponCode", Matchers.is(coupon.getCouponCode())),
                                Matchers.hasProperty("discountPercentage", Matchers.is(40)),
                                Matchers.hasProperty("active", Matchers.is(true)))));
    }

    @Test
    void updateCoupon_withValidNonCollidingCode_persistsRealChangesInH2() throws Exception {
        Coupon coupon = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        Long id = coupon.getId();
        String newCode = TestDataFactory.coupon().getCouponCode();

        mockMvc.perform(post("/admin/coupons/update/" + id)
                        .param("couponCode", newCode)
                        .param("discountPercentage", "60")
                        .param("active", "false")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attribute("successMessage",
                        "Coupon \"" + newCode + "\" updated successfully."));

        // Force the pending dirty-checked update to hit H2 for real, then
        // clear the persistence context so the re-fetch below proves the row
        // was actually persisted rather than just mutated in Java heap.
        entityManager.flush();
        entityManager.clear();

        Coupon reloaded = couponRepository.findById(id).orElseThrow();
        assertThat(reloaded.getCouponCode()).isEqualTo(newCode);
        assertThat(reloaded.getDiscountPercentage()).isEqualTo(60);
        assertThat(reloaded.isActive()).isFalse();
    }

    /**
     * Bug #9 fix proof. {@code CouponService.updateCoupon} now checks
     * {@code existsByCouponCodeAndIdNot} before saving, so renaming one
     * coupon to another real coupon's code is rejected at the app level -
     * before any UPDATE is attempted - with the same clean message {@code
     * createCoupon} already produces for its equivalent case.
     */
    @Test
    void updateCoupon_duplicateCode_isCaughtWithFriendlyMessage_andLeavesRowUnchanged() throws Exception {
        Coupon coupon1 = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        Coupon coupon2 = couponRepository.saveAndFlush(TestDataFactory.coupon(20, true));
        Long coupon1Id = coupon1.getId();
        String coupon1OriginalCode = coupon1.getCouponCode();
        String coupon2Code = coupon2.getCouponCode();

        mockMvc.perform(post("/admin/coupons/update/" + coupon1Id)
                        .param("couponCode", coupon2Code)
                        .param("discountPercentage", "99")
                        .param("active", "true")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attribute("errorMessage", "Coupon code already exists."));

        // The real, H2-persisted row for coupon1 is provably untouched.
        entityManager.flush();
        entityManager.clear();
        Optional<Coupon> reloaded = couponRepository.findById(coupon1Id);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getCouponCode()).isEqualTo(coupon1OriginalCode);
        assertThat(reloaded.get().getDiscountPercentage()).isEqualTo(10);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deleteCoupon_removesRealRowFromH2() throws Exception {
        Coupon coupon = couponRepository.saveAndFlush(TestDataFactory.coupon());
        Long id = coupon.getId();

        mockMvc.perform(post("/admin/coupons/delete/" + id).session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attribute("successMessage", "Coupon deleted successfully."));

        assertThat(couponRepository.findById(id)).isEmpty();
    }
}
