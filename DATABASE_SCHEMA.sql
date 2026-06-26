-- Pizza Ordering System — Sriya Pandey Module
-- Database schema for Aiven MySQL
-- Hibernate ddl-auto=update will create/alter tables automatically.
-- Use this script for documentation or manual provisioning.

CREATE TABLE IF NOT EXISTS customers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name      VARCHAR(60)   NOT NULL,
    last_name       VARCHAR(60)   NOT NULL,
    email           VARCHAR(120)  NOT NULL UNIQUE,
    phone           VARCHAR(20)   NOT NULL UNIQUE,
    password        VARCHAR(255)  NOT NULL,
    address         VARCHAR(255)  NOT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- If upgrading an existing database that still has the old Firebase columns,
-- drop them once (safe to ignore the error if they were never created):
--   ALTER TABLE customers DROP COLUMN firebase_uid;
--   ALTER TABLE customers DROP COLUMN email_verified;

CREATE TABLE IF NOT EXISTS admins (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    email       VARCHAR(120)  NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pizzas (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(120)  NOT NULL,
    description      VARCHAR(1000),
    category         VARCHAR(60)   NOT NULL,
    price            DECIMAL(10,2) NOT NULL,
    image_url        VARCHAR(500),
    image_public_id  VARCHAR(255),
    available        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number     VARCHAR(40)   NOT NULL UNIQUE,
    customer_id      BIGINT        NOT NULL,
    pizza_id         BIGINT        NOT NULL,
    quantity         INT           NOT NULL,
    subtotal         DECIMAL(10,2) NOT NULL,
    tax              DECIMAL(10,2) NOT NULL,
    total_amount     DECIMAL(10,2) NOT NULL,
    delivery_address VARCHAR(255)  NOT NULL,
    phone            VARCHAR(20)   NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PLACED',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_orders_pizza    FOREIGN KEY (pizza_id)    REFERENCES pizzas(id)
);

CREATE INDEX idx_pizzas_category ON pizzas(category);
CREATE INDEX idx_pizzas_name     ON pizzas(name);
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_status   ON orders(status);
