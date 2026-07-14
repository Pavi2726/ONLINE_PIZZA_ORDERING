# Setup Guide — Pizza & Drinks Ordering System

Complete deployment setup for the full application (US-001 to US-018): customer registration/login,
pizza and drink browsing, cart & checkout (with mixed pizza + drink orders), coupons, order
history/edit/cancel, and admin management of pizzas, drinks, coupons, customers, and orders.

---

## Prerequisites

- **Java 17+** (JDK 17–24 supported; Lombok 1.18.46 configured for modern JDKs)
- **Maven 3.9+**
- **MySQL database** (Aiven recommended, or any MySQL-compatible server)
- **Cloudinary account** (required for pizza and drink image uploads)

Node and npm are **not** a prerequisite for production builds: the Maven build downloads its own
pinned Node (see `frontend.node.version` in `pom.xml`) and runs `npm ci && npm run build` for
the React + Vite app in `frontend/`. The first build is slower because of this, and it needs
network access. If you already have Node installed and want a hot-reloading UI development loop,
see the **Frontend Development** section below.

---

## 1. Clone and Configure Environment

```bash
cp .env.example .env
# Edit .env with your credentials
```

Load variables before running (Linux/macOS):

```bash
export $(grep -v '^#' .env | xargs)
```

On Windows (PowerShell), set each variable individually or use a `.env`-loading script. The
application reads them via `${VARIABLE_NAME}` in `application.properties`.

---

## 2. Database (Aiven MySQL)

1. Create a MySQL service in [Aiven Console](https://console.aiven.io/) (or use any MySQL server).
2. Copy the JDBC connection string, username, and password.
3. Set in `.env`:

```env
AIVEN_DB_URL=jdbc:mysql://HOST:PORT/defaultdb?ssl-mode=REQUIRED&useSSL=true&serverTimezone=UTC
AIVEN_DB_USERNAME=avnadmin
AIVEN_DB_PASSWORD=your_password
```

Tables are created automatically via `spring.jpa.hibernate.ddl-auto=update`.
See [`DATABASE_SCHEMA.sql`](DATABASE_SCHEMA.sql) for the documented schema including the `drinks` table.

> **Note:** If upgrading from an older version that did not have the `drinks` table,
> `ddl-auto=update` will create it automatically on first boot.

---

## 3. Cloudinary (Image Uploads)

Pizza and drink images are stored in Cloudinary. Without these credentials, adding or updating
items with images will fail, but the rest of the application works normally.

1. Sign up at [cloudinary.com](https://cloudinary.com).
2. Copy Cloud Name, API Key, and API Secret from the dashboard.
3. Set in `.env`:

```env
CLOUDINARY_CLOUD_NAME=your_cloud
CLOUDINARY_API_KEY=your_key
CLOUDINARY_API_SECRET=your_secret
```

Images are uploaded to the `pizza-ordering/pizzas` folder. Only URLs are stored in MySQL.

---

## 4. Authentication (local, no external services)

Authentication is fully local. There is **no Firebase, email verification, OTP,
JWT, or Spring Security** — just BCrypt password hashing and `HttpSession`.

### 4.1 Customer Registration (US-001)

1. Customer fills the form: first name, last name, email, phone, password, confirm password, address.
2. The server validates email format, phone format, password strength, confirm-password match, and duplicate email / phone.
3. The password is hashed with `BCryptPasswordEncoder`.
4. The customer is saved into MySQL.
5. On success, the React client navigates to `/login` with a success alert.

### 4.2 Customer Login (US-002)

1. The customer is looked up by email.
2. The raw password is checked with `passwordEncoder.matches(rawPassword, storedHash)`.
3. On success, an `HttpSession` (`currentCustomer`) is created; the SPA updates its session context.
4. On failure, `"Invalid email or password."` is returned as a 401 JSON error.

No additional configuration is required for authentication.

---

## 5. Admin Account

Set **both** variables to seed the first admin on startup (when no admin exists in the DB):

```env
ADMIN_DEFAULT_EMAIL=admin@yourcompany.com
ADMIN_DEFAULT_PASSWORD=use_a_strong_password_here
```

If either is missing, **no admin is created**. Check application logs:

```
No default admin created: ADMIN_DEFAULT_EMAIL and ADMIN_DEFAULT_PASSWORD environment variables are not both set.
```

After first login, change the password by updating the BCrypt hash in the DB or creating a new
admin row manually.

---

## 6. Drink Catalogue Seeding

On first startup, if the `drinks` table is empty, `DrinkInitializer` automatically seeds 22
default drinks covering all 10 predefined categories (Soft Drinks, Diet Drinks, Zero Sugar,
Sugar-Free Drinks, Juices, Coffee, Tea, Energy Drinks, Milkshakes, Water). Seed images use
public Unsplash URLs — the admin can update images at any time via the admin panel.

No additional configuration is required for drink seeding. To disable it, simply delete or
deactivate the `DrinkInitializer` component.

---

## 7. Build and Run

### Production build (single JAR)

```bash
mvn clean package -DskipTests
java -jar target/pizza-ordering-system.jar
```

Maven will automatically run `npm ci && npm run build` inside `frontend/`, copy the Vite
output into `target/classes/static/`, and bundle everything into the JAR.

### Development run

```bash
mvn spring-boot:run
```

Application URL: http://localhost:8080

### Frontend hot-reload development

For active UI development, run both processes simultaneously:

```bash
# Terminal 1 — Spring Boot API on :8080
mvn spring-boot:run

# Terminal 2 — Vite dev server on :5173 (proxies /api to :8080)
cd frontend && npm run dev
```

Open http://localhost:5173. Auth uses the JSESSIONID cookie; the Vite proxy keeps it
same-origin, so no special CORS configuration is needed.

To skip the frontend npm build during a backend-only loop:

```bash
mvn test -DskipFrontend=true
```

---

## 8. Application URLs

| Area | URL |
|------|-----|
| Customer home | http://localhost:8080/ |
| Customer login | http://localhost:8080/login |
| Customer register | http://localhost:8080/register |
| Pizza menu | http://localhost:8080/pizzas |
| **Drinks menu** | **http://localhost:8080/drinks** |
| Cart | http://localhost:8080/cart |
| Checkout | http://localhost:8080/checkout |
| Order history | http://localhost:8080/orders/history |
| Admin login | http://localhost:8080/admin/login |
| Admin dashboard | http://localhost:8080/admin/dashboard |
| Manage pizzas | http://localhost:8080/admin/pizzas |
| **Manage drinks** | **http://localhost:8080/admin/drinks** |
| Manage coupons | http://localhost:8080/admin/coupons |
| Manage customers | http://localhost:8080/admin/customers |
| Manage orders | http://localhost:8080/admin/orders |

---

## 9. Verify Each User Story

### US-001 Registration
- Open `/register`, complete the form.
- Confirm a customer row appears in the `customers` table.
- Confirm the `password` column stores a BCrypt hash (starts with `$2a$`/`$2b$`/`$2y$`).
- You are redirected to `/login` with *"Registration successful. Please login."*

### US-002 Login
- Wrong password → `"Invalid email or password."` (401).
- Correct password → login succeeds, session created, navigated to home.
- Logout clears the session.

### US-003 Pizza List
- Open `/pizzas`, test search, category filter, price sort, pagination.

### US-004–006 Admin Pizza CRUD
- Login at `/admin/login`.
- Add pizza with image → appears in Cloudinary and DB.
- Edit pizza, replace image → old Cloudinary image deleted.
- Delete pizza → DB row and Cloudinary image removed.
- Attempting to delete a pizza that has been ordered returns a 409.
- Direct `/admin/pizzas` without admin session → 401 JSON (SPA redirects to admin login).

### US-007 Place Order (cart-based checkout)
- Login as a customer, browse `/pizzas` and `/drinks`, and add items to the cart.
- Open `/cart` to review/adjust quantities (pizzas and drinks can be mixed).
- Proceed to `/checkout`, confirm delivery details, and place the order.
- Totals (subtotal, discount if a coupon was applied, 8% tax, grand total) are calculated server-side.
- Confirmation screen at `/orders/success/{orderNumber}`.

### US-008 Apply Coupon
- From `/cart`, enter an active coupon code (created by an admin — see US-012) and submit.
- A valid, active code discounts the cart total; an invalid or inactive code shows an error.
- The coupon is re-validated when the order is actually placed.

### US-009 View Order History
- Login as a customer and open `/orders/history`.
- Confirm every past order is listed with its current status.
- Orders containing drinks show drink items alongside pizza items.

### US-010 Update Order (5-minute edit window)
- From `/orders/history`, open "Edit" on a `PLACED` order within the last 5 minutes.
- Adjust item quantities, add another pizza or drink, or update the delivery address/phone.
- After the 5-minute window (or once the order has moved past `PLACED`), editing is blocked (409).

### US-011 Cancel Order
- From `/orders/history`, cancel an order that is still `PLACED`.
- Confirm its status becomes `CANCELLED`.
- Cancelling an already-cancelled or non-PLACED order is rejected (409).

### US-012–014 Admin Coupon Management
- Login at `/admin/login`, open `/admin/coupons`.
- Create a coupon (code, discount percentage, active flag) — a duplicate code is rejected.
- Edit an existing coupon's discount or active status.
- Delete a coupon and confirm it no longer appears in the list.

### US-015 Admin: View Customers
- Open `/admin/customers`; confirm every registered customer is listed.
- Direct access without an admin session → 401 JSON.

### US-016 Admin: Manage Customers
- From `/admin/customers`, edit a customer's name, email, phone, or address.
- Changing the email/phone to one already used by a *different* customer is rejected.
- No deactivate/reactivate control by design.

### US-017 Admin: View Orders
- Open `/admin/orders`; confirm every customer's orders are listed.
- Open an order's detail page and confirm items (pizzas and/or drinks), totals, and customer info render correctly.

### US-018 Admin: Manage Orders
- From an order's detail page, move its status: `PLACED → PROCESSING → OUT_FOR_DELIVERY → DELIVERED`.
- `CANCELLED` is only reachable from `PLACED` or `PROCESSING`.
- Skipping ahead, or transitioning from a terminal status (`DELIVERED` or `CANCELLED`), is rejected (409).
- Use bulk-status to update multiple orders at once.

### Drink Management (Admin, beyond US-001–018)
- Login at `/admin/login`, open `/admin/drinks`.
- Add a new drink: fill in name, description, category (from predefined list), price, size, upload image.
- Edit an existing drink: update fields and optionally replace the image (old Cloudinary asset deleted).
- Delete a drink: DB row and Cloudinary image removed.
- Toggle the `available` flag to hide/show drinks in the customer catalogue without deleting.

---

## 10. Automated Testing

The manual checks in §9 are covered by an automated JUnit 5 suite (unit, and H2-backed
`@SpringBootTest` integration tiers) spanning all 18 user stories above. Run it with:

```bash
mvn test
```

This runs entirely against an in-memory H2 database — it does not require `.env`, network
access, or the live MySQL instance, and it never touches production data.

A Vitest suite in `frontend/` covers the API client, pagination, theme toggle, alert
auto-dismiss, and route guards:

```bash
cd frontend && npm test
```

Run everything in one command:

```bash
mvn test    # backend (JUnit 5 + H2) + frontend (Vitest, via frontend-maven-plugin)
```

---

## 11. Railway Deployment

### 11.1 Set the variables

Add all variables from `.env.example` in the Railway Variables panel:

```
AIVEN_DB_URL, AIVEN_DB_USERNAME, AIVEN_DB_PASSWORD
CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET
ADMIN_DEFAULT_EMAIL, ADMIN_DEFAULT_PASSWORD   (optional: seeds first admin)
```

### 11.2 Port binding

Railway injects a `PORT` variable. Either add a Railway variable `SERVER_PORT=$PORT` or set
`server.port=${PORT:8080}` in `application.properties` so the app binds to the assigned port.

---

## 12. Production Notes

- Set `spring.jpa.hibernate.ddl-auto=validate` in production once the schema is stable.
- Never commit `.env` or real credentials to version control.
- Use a strong `ADMIN_DEFAULT_PASSWORD` and rotate it after first login.
- Enable HTTPS in production via a reverse proxy or load balancer.
- Cloudinary images are served over HTTPS by default from Cloudinary's CDN.

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Admin login fails | Ensure `ADMIN_DEFAULT_EMAIL` + `ADMIN_DEFAULT_PASSWORD` were both set on **first** boot |
| Login always fails | Confirm the `password` column holds a BCrypt hash (not plain text) |
| Image upload fails | Verify `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` |
| DB connection refused | Check Aiven host, port, and SSL params in the JDBC URL |
| Drinks table missing | Let `ddl-auto=update` run once, or apply `DATABASE_SCHEMA.sql` manually |
| No drinks in catalogue | `DrinkInitializer` seeds on first boot; check logs for confirmation |
| Lombok compile errors on JDK 23+ | Project uses `maven.compiler.proc=full` and Lombok 1.18.46 |
| Blank page after navigation | SPA fallback is active — ensure `SpaForwardingConfig` is present and `static/index.html` exists |
| 401 on admin routes | Admin session expired or not set; navigate to `/admin/login` |
