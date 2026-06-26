# Setup Guide — Pizza Ordering System

Complete deployment setup for the Sriya Pandey module (US-001 to US-007).

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
| Admin login | http://localhost:8080/admin/login |
| Admin dashboard | http://localhost:8080/admin/dashboard |
| Manage pizzas | http://localhost:8080/admin/pizzas |

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

### US-007 Place Order
- Login as customer, order from `/pizzas`.
- Totals calculated server-side (8% tax).
- Confirmation at `/orders/success/{orderNumber}`.

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
