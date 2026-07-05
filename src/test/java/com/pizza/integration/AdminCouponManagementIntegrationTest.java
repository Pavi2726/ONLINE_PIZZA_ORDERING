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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 * <p><b>Characterization (the main point of this class):</b> {@code
 * CouponService.createCoupon} explicitly checks {@code existsByCouponCode}
 * before saving and throws a clean {@code IllegalArgumentException} - but that
 * exception is <em>never caught</em> by {@code AdminCouponController.addCoupon}
 * (no try/catch there), so it falls through to {@code GlobalExceptionHandler}'s
 * generic {@code IllegalArgumentException} handler and renders as an HTTP 400
 * {@code error} page.
 *
 * <p>{@code CouponService.updateCoupon} has <em>no such check at all</em> - it
 * blindly sets the new code and saves, relying entirely on the database's
 * unique constraint on {@code coupons.coupon_code}. {@code
 * AdminCouponController.updateCoupon} <em>does</em> wrap that call in a
 * try/catch, so in real (single-transaction-per-request) production use the
 * resulting {@code DataIntegrityViolationException} - thrown synchronously
 * when the surrounding {@code @Transactional} service method commits - is
 * caught there and redirected back to the list with a leaky, unfriendly
 * {@code errorMessage} flash attribute (the raw SQL exception text), not a
 * clean message and not a 500. See {@link #updateCoupon_duplicateCode_hasNoAppLevelCheck_isCaughtButLeaksRawSqlErrorMessage_andLeavesRowUnchanged()}
 * for why that one test method deliberately opts out of this class's inherited
 * transaction wrapping to observe that real behaviour.
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
     * The characterization test. {@code CouponService.updateCoupon} performs
     * no duplicate-code check of its own (unlike {@code createCoupon}), so
     * renaming one coupon to another real coupon's code relies purely on the
     * database's unique constraint on {@code coupons.coupon_code}.
     *
     * <p>This method is deliberately annotated {@code @Transactional(propagation
     * = NOT_SUPPORTED)}, opting this one test out of the class's inherited
     * (from {@link AbstractIntegrationTest}) per-test rollback transaction.
     * Verified empirically: when this test runs <em>inside</em> that inherited
     * wrapping transaction (the default for every other test here), {@code
     * CouponService.updateCoupon}'s own {@code @Transactional} merely joins the
     * already-open test transaction instead of owning/committing one, so the
     * broken UPDATE is never flushed to H2 during the request - the controller
     * sees no exception at all and happily redirects with a false-positive
     * "updated successfully" flash message. That is a real, interesting bug in
     * its own right, but it is an artifact of this test harness's shared
     * persistence context, not what a real single-request production call
     * does. Suspending the test transaction here lets {@code updateCoupon}'s
     * {@code @Transactional} be the genuine, request-scoped transaction
     * boundary - matching production - so its commit-time flush (and the
     * resulting {@code DataIntegrityViolationException}) happens synchronously
     * inside the {@code couponService.updateCoupon(...)} call, exactly as it
     * would for a real admin request. That exception is then caught by {@code
     * AdminCouponController.updateCoupon}'s try/catch, which is confirmed below.
     *
     * <p>Because this method does not run in a rolled-back transaction, it
     * cleans up everything it writes in a {@code finally} block.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updateCoupon_duplicateCode_hasNoAppLevelCheck_isCaughtButLeaksRawSqlErrorMessage_andLeavesRowUnchanged() throws Exception {
        Coupon coupon1 = couponRepository.saveAndFlush(TestDataFactory.coupon(10, true));
        Coupon coupon2 = couponRepository.saveAndFlush(TestDataFactory.coupon(20, true));
        Long coupon1Id = coupon1.getId();
        String coupon1OriginalCode = coupon1.getCouponCode();
        String coupon2Code = coupon2.getCouponCode();

        try {
            mockMvc.perform(post("/admin/coupons/update/" + coupon1Id)
                            .param("couponCode", coupon2Code)
                            .param("discountPercentage", "99")
                            .param("active", "true")
                            .session(adminSession()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/coupons"))
                    .andExpect(flash().attributeExists("errorMessage"))
                    .andExpect(flash().attribute("errorMessage",
                            Matchers.not(Matchers.containsString("updated successfully"))))
                    // Unlike createCoupon's clean "Coupon code already exists."
                    // message, updateCoupon has no such check, so the flashed
                    // text is the raw, unfriendly constraint-violation message
                    // bubbling up from H2/Hibernate.
                    .andExpect(flash().attribute("errorMessage",
                            Matchers.containsStringIgnoringCase("constraint")));

            // The real, H2-persisted row for coupon1 is provably untouched -
            // the failed commit rolled the whole update back.
            Optional<Coupon> reloaded = couponRepository.findById(coupon1Id);
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().getCouponCode()).isEqualTo(coupon1OriginalCode);
            assertThat(reloaded.get().getDiscountPercentage()).isEqualTo(10);
        } finally {
            couponRepository.deleteById(coupon1Id);
            couponRepository.deleteById(coupon2.getId());
        }
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
