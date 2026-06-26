-- =====================================================================
-- RESET_USERS.sql — Pizza Ordering System
-- Clears all application USERS and their dependent data from MySQL so the
-- app can be tested from a clean state.
--
-- CLEARS:   orders, customers, admins
-- PRESERVES: pizzas (and all pizza/category/seed data), Cloudinary images,
--            and application configuration.
--
-- Firebase Authentication users are NOT touched (delete them manually in the
-- Firebase Console). The default admin is re-seeded automatically on the next
-- application startup if ADMIN_DEFAULT_EMAIL and ADMIN_DEFAULT_PASSWORD are set.
--
-- Safe to re-run. Reusable.
-- =====================================================================

-- Disable FK checks so order of deletion never blocks on constraints.
SET FOREIGN_KEY_CHECKS = 0;

-- Child table first (orders references customers and pizzas).
DELETE FROM orders;

-- User tables.
DELETE FROM customers;
DELETE FROM admins;

-- Re-enable FK checks.
SET FOREIGN_KEY_CHECKS = 1;

-- Reset identity counters so new IDs start at 1 again.
ALTER TABLE orders    AUTO_INCREMENT = 1;
ALTER TABLE customers AUTO_INCREMENT = 1;
ALTER TABLE admins    AUTO_INCREMENT = 1;

-- pizzas is intentionally left untouched.
