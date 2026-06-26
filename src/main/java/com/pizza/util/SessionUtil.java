package com.pizza.util;

import com.pizza.entity.Admin;
import com.pizza.entity.Customer;
import jakarta.servlet.http.HttpSession;

/**
 * Helper for managing the logged-in principals in the {@link HttpSession}.
 * Customers and admins are kept under separate, non-overlapping attribute keys
 * so the two sessions are never mixed. Session-based authentication is used
 * deliberately (no Spring Security / JWT).
 */
public final class SessionUtil {

    /** Session attribute key holding the logged-in {@link Customer}. */
    public static final String CURRENT_CUSTOMER = "currentCustomer";

    /** Session attribute key holding the logged-in {@link Admin}. */
    public static final String CURRENT_ADMIN = "currentAdmin";

    private SessionUtil() {
    }

    // --------------------------- Customer ---------------------------

    public static void loginCustomer(HttpSession session, Customer customer) {
        session.setAttribute(CURRENT_CUSTOMER, customer);
    }

    public static Customer getCurrentCustomer(HttpSession session) {
        Object value = session.getAttribute(CURRENT_CUSTOMER);
        return value instanceof Customer customer ? customer : null;
    }

    public static boolean isCustomerLoggedIn(HttpSession session) {
        return getCurrentCustomer(session) != null;
    }

    /** Removes only the customer principal, leaving any admin session intact. */
    public static void logoutCustomer(HttpSession session) {
        session.removeAttribute(CURRENT_CUSTOMER);
    }

    // ----------------------------- Admin ----------------------------

    public static void loginAdmin(HttpSession session, Admin admin) {
        session.setAttribute(CURRENT_ADMIN, admin);
    }

    public static Admin getCurrentAdmin(HttpSession session) {
        Object value = session.getAttribute(CURRENT_ADMIN);
        return value instanceof Admin admin ? admin : null;
    }

    public static boolean isAdminLoggedIn(HttpSession session) {
        return getCurrentAdmin(session) != null;
    }

    /** Removes only the admin principal, leaving any customer session intact. */
    public static void logoutAdmin(HttpSession session) {
        session.removeAttribute(CURRENT_ADMIN);
    }
}
