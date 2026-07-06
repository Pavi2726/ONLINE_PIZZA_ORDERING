-- =====================================================================
-- RESET_USERS.sql — Pizza Ordering System
-- Clears all application USERS and their dependent data from MySQL so the
-- app can be tested from a clean state.
--
-- CLEARS:    order_items, orders, cart_item, cart, coupons, customers, admins
-- PRESERVES: pizzas (and all pizza/category/seed data), Cloudinary images,
--            and application configuration.
--
-- order_items/cart_item are cleared before orders/cart since they hold FK
-- references to them; cart/cart_item are cleared here too even though they
-- aren't FK-linked to customers (Cart.username is a plain string, not a
-- foreign key) because every cart row belongs to a customer being wiped
-- below, and leaving it behind would just be orphaned data pointing at a
-- deleted account.
--
-- Firebase Authentication users are NOT touched (delete them manually in the
-- Firebase Console). The default admin is re-seeded automatically on the next
-- application startup if ADMIN_DEFAULT_EMAIL and ADMIN_DEFAULT_PASSWORD are set.
--
-- Safe to re-run. Reusable.
-- =====================================================================

-- Disable FK checks so order of deletion never blocks on constraints.
SET FOREIGN_KEY_CHECKS = 0;

-- Child tables first (reference orders/cart/customers/pizzas).
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cart_item;
DELETE FROM cart;

-- User tables.
DELETE FROM customers;
DELETE FROM admins;

-- Coupons are user-facing seed/promo data, not tied to any customer, but
-- cleared here too since a clean-state reset shouldn't leave stale promo
-- codes behind either.
DELETE FROM coupons;

-- Re-enable FK checks.
SET FOREIGN_KEY_CHECKS = 1;

-- Reset identity counters so new IDs start at 1 again.
ALTER TABLE order_items AUTO_INCREMENT = 1;
ALTER TABLE orders      AUTO_INCREMENT = 1;
ALTER TABLE cart_item   AUTO_INCREMENT = 1;
ALTER TABLE cart        AUTO_INCREMENT = 1;
ALTER TABLE customers   AUTO_INCREMENT = 1;
ALTER TABLE admins      AUTO_INCREMENT = 1;
ALTER TABLE coupons     AUTO_INCREMENT = 1;

-- pizzas is intentionally left untouched.
