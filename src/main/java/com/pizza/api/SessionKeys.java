package com.pizza.api;

/** Session attributes the API reads or writes beyond the two principals in {@code SessionUtil}. */
public final class SessionKeys {

    /**
     * The coupon applied to the current cart. Kept in the session rather than on the
     * Cart row exactly as the server-rendered app did — the SPA still carries
     * JSESSIONID, so this works unchanged and needs no schema change to the shared
     * database. Consequence, unchanged from before: the coupon is per-session, not
     * per-cart, so two tabs can disagree.
     */
    public static final String APPLIED_COUPON = "appliedCoupon";

    private SessionKeys() {
    }
}
