# Pizza Ordering System — Sriya Pandey Module

Spring Boot + Thymeleaf web application implementing **US-001 to US-007**: customer
registration with local BCrypt authentication, session-based login, pizza catalogue
browsing, **admin-only** pizza management (Cloudinary), and order placement.

> Scope: only Sriya Pandey's assigned stories. Coupons, cart, payments, customer
> management, order history, and admin order management belong to other teammates.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3.x, Spring MVC, Spring Data JPA |
| Frontend | Thymeleaf, Bootstrap 5, Bootstrap Icons |
| Database | Aiven MySQL (never H2) |
| Customer auth | `HttpSession` + BCrypt (no Spring Security / JWT / email verification) |
| Admin auth | Separate `HttpSession` key (`currentAdmin`) |
| Images | Cloudinary (URL in DB only) |
| Build | Maven |

---

## User Stories

| ID | Feature | Access |
|----|---------|--------|
| US-001 | Customer registration (local BCrypt) | Public |
| US-002 | Customer login / logout | Public |
| US-003 | View / search / filter pizza list | Public |
| US-004 | Add pizza | **Admin only** (`/admin/pizzas/add`) |
| US-005 | Update pizza | **Admin only** (`/admin/pizzas/edit/{id}`) |
| US-006 | Delete pizza | **Admin only** (`/admin/pizzas/delete/{id}`) |
| US-007 | Place order | **Customer only** (`/orders/**`) |

---

## Quick Start

1. Copy [`.env.example`](.env.example) and set all required variables.
2. Follow [`SETUP_GUIDE.md`](SETUP_GUIDE.md) for Cloudinary and Aiven setup.
3. Run:

```bash
export $(grep -v '^#' .env | xargs)   # or set vars manually
mvn spring-boot:run
```

4. Open http://localhost:8080 (customer) and http://localhost:8080/admin/login (admin).

---

## Authorization Model

| Session key | Used by | Protected routes |
|-------------|---------|------------------|
| `currentCustomer` | Customers | `/orders/**` via `CustomerAuthInterceptor` |
| `currentAdmin` | Admins | `/admin/**` via `AdminAuthInterceptor` |

Sessions are **never mixed**. Logout clears only the corresponding principal.

---

## Admin Account

No admin credentials are hardcoded. On first startup, an admin is created **only**
when **both** environment variables are set:

- `ADMIN_DEFAULT_EMAIL`
- `ADMIN_DEFAULT_PASSWORD`

If either is missing, no admin is created and a warning is logged.

---

## Documentation

| File | Purpose |
|------|---------|
| [`SETUP_GUIDE.md`](SETUP_GUIDE.md) | Step-by-step deployment setup |
| [`PROJECT_STRUCTURE.md`](PROJECT_STRUCTURE.md) | Codebase layout |
| [`DATABASE_SCHEMA.sql`](DATABASE_SCHEMA.sql) | Table definitions |
| [`.env.example`](.env.example) | Environment variable template |
| [`POSTMAN_COLLECTION.json`](POSTMAN_COLLECTION.json) | Route reference for testing |

---

## Authentication Flow

Authentication is fully local — no Firebase, email verification, OTP, JWT, or
Spring Security.

**Registration (US-001):**

```
User submits form (name, email, phone, password, confirm password, address)
→ validate email/phone format, password strength, confirm-password match
→ check duplicate email and phone in MySQL
→ hash password once with BCrypt
→ save customer in MySQL
→ redirect to /login with "Registration successful. Please login."
```

**Login (US-002):**

```
User submits email + password
→ find customer by email
→ passwordEncoder.matches(rawPassword, storedHash)
→ on success: create HttpSession (currentCustomer) → redirect to dashboard
→ on failure: "Invalid email or password."
```

Passwords are hashed exactly once with `BCryptPasswordEncoder` and are never
stored or logged in plain text.

---

## Production Checklist

- [ ] All env vars set (see `.env.example`)
- [ ] `ADMIN_DEFAULT_*` set for first admin, then rotate password
- [ ] `spring.jpa.hibernate.ddl-auto=update` reviewed for production (consider `validate`)
- [ ] Cloudinary folder permissions configured
- [ ] Aiven MySQL SSL enabled in JDBC URL
