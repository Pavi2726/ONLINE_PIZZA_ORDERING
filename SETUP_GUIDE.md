# Setup Guide — Pizza Ordering System

Complete deployment setup for the full application (US-001 to US-018): customer registration/login, pizza browsing, cart & checkout, coupons, order history/edit/cancel, and admin management of pizzas, coupons, customers, and orders.

---

## Prerequisites

- Java 17+ (JDK 17–24 supported; Lombok 1.18.46 configured for modern JDKs)
- Maven 3.9+
- Aiven MySQL database
- Cloudinary account

---

## 1. Clone and Configure Environment

```bash
cp .env.example .env
# Edit .env with your credentials
```

Load variables before running:

```bash
export $(grep -v '^#' .env | xargs)
```

---

## 2. Aiven MySQL

1. Create a MySQL service in [Aiven Console](https://console.aiven.io/).
2. Copy the JDBC connection string, username, and password.
3. Set in `.env`:

```
AIVEN_DB_URL=jdbc:mysql://HOST:PORT/defaultdb?ssl-mode=REQUIRED&useSSL=true&serverTimezone=UTC
AIVEN_DB_USERNAME=avnadmin
AIVEN_DB_PASSWORD=your_password
```

Tables are created automatically via `spring.jpa.hibernate.ddl-auto=update`.
See [`DATABASE_SCHEMA.sql`](DATABASE_SCHEMA.sql) for the documented schema.

---

## 3. Cloudinary

1. Sign up at [cloudinary.com](https://cloudinary.com).
2. Copy Cloud Name, API Key, and API Secret from the dashboard.
3. Set in `.env`:

```
CLOUDINARY_CLOUD_NAME=your_cloud
CLOUDINARY_API_KEY=your_key
CLOUDINARY_API_SECRET=your_secret
```

Pizza images are uploaded to folder `pizza-ordering/pizzas`. Only URLs are stored in MySQL.

---

## 4. Authentication (local, no external services)

Authentication is fully local. There is **no Firebase, email verification, OTP,
JWT, or Spring Security** — just BCrypt password hashing and `HttpSession`.

### 4.1 Registration flow (US-001)

1. Customer fills the form: first name, last name, email, phone, password,
   confirm password, address.
2. The server validates email format, phone format, password strength,
   confirm-password match, and duplicate email / phone.
3. The password is hashed once with `BCryptPasswordEncoder`.
4. The customer is saved directly into MySQL.
5. The user is redirected to `/login` with *"Registration successful. Please login."*

### 4.2 Login flow (US-002)

1. The customer is looked up by email.
2. The raw password is checked with `passwordEncoder.matches(rawPassword, storedHash)`.
3. On success, an `HttpSession` (`currentCustomer`) is created and the user is
   redirected to the dashboard.
4. On failure, *"Invalid email or password."* is shown.

No additional configuration is required for authentication.

---

## 5. Admin Account

Set **both** variables to seed the first admin on startup (when no admin exists):

```
ADMIN_DEFAULT_EMAIL=admin@yourcompany.com
ADMIN_DEFAULT_PASSWORD=use_a_strong_password_here
```

If either is missing, **no admin is created**. Check application logs:

```
No default admin created: ADMIN_DEFAULT_EMAIL and ADMIN_DEFAULT_PASSWORD environment variables are not both set.
```

After first login, change the password by updating the DB hash or creating a new admin manually.

---

## 6. Build and Run

```bash
mvn clean package -DskipTests
java -jar target/pizza-ordering-system.jar
```

Or for development:

```bash
mvn spring-boot:run
```

Application URL: http://localhost:8080

| Area | URL |
|------|-----|
| Customer home | http://localhost:8080/ |
| Customer login | http://localhost:8080/login |
| Customer register | http://localhost:8080/register |
| Pizza menu | http://localhost:8080/pizzas |
| Cart | http://localhost:8080/cart |
| Checkout | http://localhost:8080/orders/checkout |
| Order history | http://localhost:8080/orders/history |
| Admin login | http://localhost:8080/admin/login |
| Admin dashboard | http://localhost:8080/admin/dashboard |
| Manage pizzas | http://localhost:8080/admin/pizzas |
| Manage coupons | http://localhost:8080/admin/coupons |
| Manage customers | http://localhost:8080/admin/customers |
| Manage orders | http://localhost:8080/admin/orders |

---

## 7. Verify Each User Story

### US-001 Registration
- Open `/register`, complete the form.
- Confirm a customer row appears in the `customers` table.
- Confirm the `password` column stores a BCrypt hash (starts with `$2a$`/`$2b$`/`$2y$`).
- You are redirected to `/login` with *"Registration successful. Please login."*

### US-002 Login
- Wrong password → *"Invalid email or password."*
- Correct password → login succeeds, session created, redirected to home.
- `/logout` clears the session.

### US-003 Pizza List
- Open `/pizzas`, test search, category filter, price sort, pagination.

### US-004–006 Admin Pizza CRUD
- Login at `/admin/login`.
- Add pizza with image → appears in Cloudinary and DB.
- Edit pizza, replace image → old Cloudinary image deleted.
- Delete pizza → DB row and Cloudinary image removed.
- Direct `/admin/pizzas` without login → redirected to admin login.

### US-007 Place Order (cart-based checkout)
- Login as a customer, browse `/pizzas`, and add one or more pizzas to the cart.
- Open `/cart` to review/adjust quantities, then proceed to `/orders/checkout`.
- Submit the order; totals (subtotal, discount if a coupon was applied, 8% tax, grand total) are calculated server-side.
- Confirmation at `/orders/success/{orderNumber}`.
- Note: the app used to place orders directly from a single-pizza form at `/orders/new`; that route is no longer linked from any page and is not part of the current flow.

### US-008 Apply Coupon
- From `/cart`, enter an active coupon code (created by an admin — see US-012) and submit.
- A valid, active code discounts the cart total; an unknown or inactive code shows an error and leaves the cart unchanged.
- The coupon is re-checked when the order is actually placed, not just when it's applied to the cart.

### US-009 View Order History
- Login as a customer and open `/orders/history`.
- Confirm every past order placed by that customer is listed with its current status.

### US-010 Update Order
- From `/orders/history`, open "Edit" on an order that is still `PLACED` and was placed within the last 5 minutes.
- Adjust item quantities, add another pizza, or update the delivery address/phone, and confirm the changes persist.
- After the 5-minute window (or once the order has moved past `PLACED`), editing is no longer available.

### US-011 Cancel Order
- From `/orders/history`, cancel an order that is still `PLACED`.
- Confirm its status becomes `CANCELLED`.
- Attempting to cancel an already-cancelled order, or one that's moved past `PLACED`, is rejected.

### US-012–014 Admin Coupon Management
- Login at `/admin/login`, open `/admin/coupons`.
- Create a coupon (code, discount percentage, active flag) — a duplicate code is rejected.
- Edit an existing coupon's discount or active status.
- Delete a coupon and confirm it no longer appears in the list.

### US-015 Admin: View Customers
- Open `/admin/customers`; confirm every registered customer is listed.
- Direct access without an admin session redirects to `/admin/login`.

### US-016 Admin: Manage Customers
- From `/admin/customers`, edit a customer's name, email, phone, or address.
- Changing the email/phone to one already used by a *different* customer is rejected with a validation error.
- This story is edit-only by design — there is no deactivate/reactivate control.

### US-017 Admin: View Orders
- Open `/admin/orders`; confirm every customer's orders are listed.
- Open an order's detail page and confirm items, totals, and customer info render correctly.

### US-018 Admin: Manage Orders
- From an order's detail page, move its status forward: `PLACED → PROCESSING → OUT_FOR_DELIVERY → DELIVERED`.
- `CANCELLED` is only reachable from `PLACED` or `PROCESSING`.
- Skipping ahead in the sequence, or transitioning out of a terminal status (`DELIVERED` or `CANCELLED`), is rejected.
- Confirm the customer's `/orders/history` page picks up the new status.

---

## 8. Automated Testing

The manual checks in §7 are also covered by an automated JUnit 5 suite (unit, `@WebMvcTest` controller, and H2-backed `@SpringBootTest` integration tiers) spanning all 18 user stories above. Run it with:

```bash
mvn test
```

This runs entirely against an in-memory H2 database — it does not require `.env`, network access, or the live Aiven MySQL instance, and it never touches production data.

---

## 9. Railway Deployment

### 9.1 Set the variables

Add all variables from `.env.example` in the Railway Variables panel:

```
AIVEN_DB_URL, AIVEN_DB_USERNAME, AIVEN_DB_PASSWORD
CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET
ADMIN_DEFAULT_EMAIL, ADMIN_DEFAULT_PASSWORD   (optional: seeds first admin)
```

### 9.2 Port binding

Railway injects a `PORT` variable. Either add a variable `SERVER_PORT=$PORT` or set
`server.port=${PORT:8080}` in `application.properties` so the app binds correctly.

---

## 10. Production Notes

- Set `spring.jpa.hibernate.ddl-auto=validate` in production after schema is stable.
- Never commit `.env` or real credentials.
- Use strong `ADMIN_DEFAULT_PASSWORD` and rotate after first login.
- Enable HTTPS in production (reverse proxy / load balancer).

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Admin login fails | Ensure `ADMIN_DEFAULT_EMAIL` + `ADMIN_DEFAULT_PASSWORD` were set on first boot |
| Login always fails | Confirm the `password` column holds a BCrypt hash (not plain text) |
| Image upload fails | Verify `CLOUDINARY_*` variables |
| DB connection refused | Check Aiven host, port, SSL params in JDBC URL |
| Lombok compile errors on JDK 23+ | Project uses `maven.compiler.proc=full` and Lombok 1.18.46 |
