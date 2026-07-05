package com.pizza.controller;

import com.pizza.entity.Coupon;
import com.pizza.service.CouponService;
import com.pizza.testsupport.TestDataFactory;
import com.pizza.util.SessionUtil;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code @WebMvcTest} slice for {@link AdminCouponController}. Every route
 * lives under {@code /admin/coupons/**}, which {@code WebMvcConfig} protects
 * with {@code AdminAuthInterceptor} (no exclusions for this sub-path), so an
 * admin session is required everywhere.
 */
@WebMvcTest(AdminCouponController.class)
class AdminCouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CouponService couponService;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUtil.CURRENT_ADMIN, TestDataFactory.admin());
        return session;
    }

    static Stream<Arguments> everyRoute() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/admin/coupons"),
                Arguments.of(HttpMethod.GET, "/admin/coupons/add"),
                Arguments.of(HttpMethod.POST, "/admin/coupons/add"),
                Arguments.of(HttpMethod.GET, "/admin/coupons/edit/1"),
                Arguments.of(HttpMethod.POST, "/admin/coupons/update/1"),
                Arguments.of(HttpMethod.POST, "/admin/coupons/delete/1"));
    }

    @ParameterizedTest(name = "{0} {1} with no admin session redirects to /admin/login")
    @MethodSource("everyRoute")
    void everyRoute_withNoAdminSession_redirectsToAdminLogin(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verifyNoInteractions(couponService);
    }

    // --------------------------------------------------------------------- list

    @Test
    void list_withAdminSession_rendersCouponList() throws Exception {
        Coupon coupon = TestDataFactory.coupon();
        when(couponService.findAll()).thenReturn(List.of(coupon));

        mockMvc.perform(request(HttpMethod.GET, "/admin/coupons").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-coupon-list"))
                .andExpect(model().attribute("coupons", List.of(coupon)));
    }

    // ---------------------------------------------------------------- add form

    @Test
    void showAddForm_withAdminSession_rendersFormWithEmptyCommandObject() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/admin/coupons/add").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("add-coupon"))
                .andExpect(model().attributeExists("couponDTO"));
    }

    @Test
    void addCoupon_withValidData_redirectsToListWithSuccessFlash() throws Exception {
        Coupon saved = TestDataFactory.coupon(15, true);
        when(couponService.createCoupon(any())).thenReturn(saved);

        mockMvc.perform(request(HttpMethod.POST, "/admin/coupons/add")
                        .param("couponCode", saved.getCouponCode())
                        .param("discountPercentage", "15")
                        .param("active", "true")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(couponService).createCoupon(any());
    }

    @Test
    void addCoupon_withBlankCouponCode_reRendersFormWithValidationError_notA500() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/admin/coupons/add")
                        .param("couponCode", "")
                        .param("discountPercentage", "15")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("add-coupon"))
                .andExpect(model().attributeHasFieldErrors("couponDTO", "couponCode"));

        verifyNoInteractions(couponService);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deleteCoupon_withAdminSession_redirectsToListAndCallsService() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/admin/coupons/delete/1").session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/coupons"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(couponService).deleteCoupon(eq(1L));
    }
}
