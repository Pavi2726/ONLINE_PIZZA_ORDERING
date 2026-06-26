-- =====================================================================
-- RESET_DATABASE.sql
-- ---------------------------------------------------------------------
-- Wipes ALL application data while preserving the table structure, so
-- the app can be tested from a completely fresh state.
--
--   * Keeps every table (no DROP).
--   * Removes every row (TRUNCATE).
--   * Resets AUTO_INCREMENT back to 1 (TRUNCATE does this automatically).
--   * Disables foreign-key checks during the wipe to avoid constraint
--     ordering problems, then re-enables them.
--
-- The default admin is intentionally removed here; the application's
-- startup seeder (AdminInitializer) recreates it on the next boot when
-- ADMIN_DEFAULT_EMAIL and ADMIN_DEFAULT_PASSWORD are set.
--
-- Usage (Aiven MySQL):
--   mysql -h <host> -P <port> -u <user> -p<password> defaultdb < RESET_DATABASE.sql
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ---- Child / dependent tables first (defensive ordering) ------------
TRUNCATE TABLE orders;

-- ---- Parent / independent tables -----------------------------------
TRUNCATE TABLE customers;
TRUNCATE TABLE admins;
TRUNCATE TABLE pizzas;

-- ---------------------------------------------------------------------
-- Optional tables: uncomment any that exist in future versions of the
-- schema. Leaving a TRUNCATE for a non-existent table would error, so
-- they are commented out until the table is actually added.
-- ---------------------------------------------------------------------
-- TRUNCATE TABLE order_items;
-- TRUNCATE TABLE cart_items;
-- TRUNCATE TABLE cart;
-- TRUNCATE TABLE payments;
-- TRUNCATE TABLE coupons;
-- TRUNCATE TABLE pizza_categories;
-- TRUNCATE TABLE roles;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- Verification (run manually if desired):
--   SELECT 'customers' AS t, COUNT(*) FROM customers
--   UNION ALL SELECT 'admins', COUNT(*) FROM admins
--   UNION ALL SELECT 'pizzas', COUNT(*) FROM pizzas
--   UNION ALL SELECT 'orders', COUNT(*) FROM orders;
-- =====================================================================
