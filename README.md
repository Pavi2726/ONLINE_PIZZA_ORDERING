# Online Pizza Ordering System

An Online Pizza & Drinks Ordering System built using **Spring Boot**, **React + Vite**, and **MySQL**. The application enables customers to browse pizzas and drinks, place orders, apply coupons, manage their orders, and allows administrators to manage pizzas, drinks, customers, orders, and coupons.

The frontend is a React single-page app (SPA) in [`frontend/`](frontend/) built with Vite, talking to a JSON REST API under `/api/**`. Maven builds the SPA into the Spring Boot JAR via the `frontend-maven-plugin`, so the whole application ships and runs as one artifact on port 8080.

---

## Features

### Customer Features

* Customer Registration
* Secure Login & Logout
* Browse Pizza Catalogue (search, filter, sort)
* Browse Drinks Catalogue (search, filter by category, sort by price)
* Add Pizzas and Drinks to Cart
* Apply Coupons
* Place Orders (cart-based checkout)
* View Order Confirmation
* View Order History
* Update Pending Orders (within 5-minute edit window)
* Cancel Orders
* Reorder Past Orders

### Admin Features

* Pizza Management (Add, Update, Delete with Cloudinary image upload)
* **Drink Management (Add, Update, Delete with Cloudinary image upload)**
* Coupon Management (Create, Update, Delete)
* Customer Management (View, Edit)
* Order Management (View, Status transitions, Bulk status update)
* Dashboard with stats for pizzas and drinks

---

## Technology Stack

| Layer          | Technology                                        |
| -------------- | ------------------------------------------------- |
| Backend        | Java 17, Spring Boot 3.x                          |
| Frontend       | React 18, React Router v6, Bootstrap 5, Vite 5   |
| Frontend build | Vite (via frontend-maven-plugin in Maven)         |
| Database       | MySQL (Aiven) / H2 (tests)                        |
| ORM            | Spring Data JPA (Hibernate)                       |
| Authentication | BCrypt + HttpSession (JSESSIONID)                 |
| Image Storage  | Cloudinary                                        |
| Build Tool     | Maven                                             |
| Testing        | JUnit 5, Mockito, Vitest, Testing Library         |

---

## User Stories

| ID     | Description                        |
| ------ | ---------------------------------- |
| US-001 | Customer Registration              |
| US-002 | Customer Login                     |
| US-003 | View Pizza List                    |
| US-004 | Add Pizza (Admin)                  |
| US-005 | Update Pizza (Admin)               |
| US-006 | Delete Pizza (Admin)               |
| US-007 | Place Order (cart-based checkout)  |
| US-008 | Apply Coupon                       |
| US-009 | View Order History                 |
| US-010 | Update Order (5-minute window)     |
| US-011 | Cancel Order                       |
| US-012 | Create Coupon (Admin)              |
| US-013 | Update Coupon (Admin)              |
| US-014 | Delete Coupon (Admin)              |
| US-015 | View Customers (Admin)             |
| US-016 | Manage Customers (Admin)           |
| US-017 | View Orders (Admin)                |
| US-018 | Manage Orders / Status (Admin)     |

---

## Project Structure

```text
ONLINE_PIZZA_ORDERING/
├── pom.xml
├── README.md
├── SETUP_GUIDE.md
├── PROJECT_STRUCTURE.md
├── DATABASE_SCHEMA.sql
├── POSTMAN_COLLECTION.json
├── .env / .env.example
├── Dockerfile
│
├── src/
│   ├── main/
│   │   ├── java/com/pizza/
│   │   │   ├── api/          REST controllers + response DTOs (the whole HTTP surface)
│   │   │   ├── service/      business logic
│   │   │   ├── repository/   Spring Data JPA repositories
│   │   │   ├── entity/       JPA entities (incl. Drink)
│   │   │   ├── dto/          form-backing request DTOs (incl. DrinkDTO)
│   │   │   ├── config/       auth interceptors, SPA fallback, initializers (incl. DrinkInitializer)
│   │   │   ├── exception/    custom exception classes
│   │   │   └── util/         SessionUtil
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/       ← Vite build output lands here at package time
│   └── test/
│
└── frontend/                 React SPA (Vite 5)
    ├── index.html
    ├── vite.config.js        /api proxy (dev), build → target/classes/static
    ├── package.json
    └── src/
        ├── main.jsx          Bootstrap CSS/JS + providers + router
        ├── App.jsx           Route table; ApiBridge turns a 401 into a redirect
        ├── api/
        │   ├── client.js     fetch wrapper: credentials, ApiError, 401 hook
        │   └── index.js      auth / pizzas / drinks / cart / orders / admin endpoints
        ├── context/
        │   ├── SessionContext.jsx
        │   └── AlertContext.jsx
        ├── hooks/            useApi, usePagination, useTheme
        ├── lib/
        │   └── format.js     money / dateTime / abbreviate helpers
        ├── styles/
        │   └── styles.css
        ├── components/
        │   ├── layout/
        │   │   ├── CustomerLayout.jsx
        │   │   └── AdminLayout.jsx
        │   ├── AlertStack.jsx
        │   ├── Guards.jsx
        │   ├── OrderStatusStepper.jsx
        │   ├── Pagination.jsx
        │   ├── StatusBadge.jsx
        │   ├── SubmitButton.jsx
        │   └── ThemeToggle.jsx
        ├── pages/
        │   ├── Home.jsx
        │   ├── Login.jsx
        │   ├── Register.jsx
        │   ├── PizzaList.jsx
        │   ├── DrinkList.jsx        ← Drinks catalogue (customer-facing)
        │   ├── Cart.jsx
        │   ├── Checkout.jsx
        │   ├── OrderSuccess.jsx
        │   ├── OrderHistory.jsx
        │   ├── EditOrder.jsx
        │   └── NotFound.jsx
        └── pages/admin/
            ├── AdminLogin.jsx
            ├── Dashboard.jsx
            ├── AdminPizzaList.jsx
            ├── PizzaForm.jsx
            ├── AdminDrinkList.jsx   ← Admin drink management list
            ├── DrinkForm.jsx        ← Add / edit drink form
            ├── CouponList.jsx
            ├── CouponForm.jsx
            ├── CustomerList.jsx
            ├── CustomerForm.jsx
            ├── AdminOrderList.jsx
            └── AdminOrderDetail.jsx
```

---

## Getting Started

### Prerequisites

* Java 17 or later
* Maven 3.9+
* MySQL (Aiven recommended) or any MySQL-compatible database
* Cloudinary account (for image uploads)

Node and npm are **not** a prerequisite for production builds — the Maven build downloads its own pinned Node via `frontend-maven-plugin`. For hot-reload development, local Node is needed.

### Setup

1. Clone the repository.
2. Copy `.env.example` to `.env` and fill in your credentials.
3. Create the database using `DATABASE_SCHEMA.sql` (or let `ddl-auto=update` create tables automatically).
4. Run the application:

```bash
mvn spring-boot:run
```

The application will be available at:

```
http://localhost:8080
```

See [SETUP_GUIDE.md](SETUP_GUIDE.md) for full step-by-step instructions including Cloudinary setup, admin seeding, and deployment.

---

## Documentation

* **[SETUP_GUIDE.md](SETUP_GUIDE.md)** — Complete project setup and deployment instructions
* **[PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)** — Detailed architecture and file-level annotations
* **[DATABASE_SCHEMA.sql](DATABASE_SCHEMA.sql)** — Full database schema
* **[POSTMAN_COLLECTION.json](POSTMAN_COLLECTION.json)** — API collection for testing
* **[.env.example](.env.example)** — Environment variable template

---

## Development

The application follows a layered architecture:

```
React SPA (browser)  ── fetch, JSESSIONID cookie ──┐
                                                    ↓
@RestController (com.pizza.api)  ← interceptors: 401 JSON on guarded routes
        ↓
Service     ← @Transactional business logic
        ↓
Repository  ← Spring Data JPA
        ↓
MySQL (production) / H2 (test suite)
```

### Running with hot reload (recommended for UI development)

```bash
# Terminal 1 — API on :8080
mvn spring-boot:run

# Terminal 2 — SPA on :5173 with proxy to :8080
cd frontend && npm run dev
```

Open http://localhost:5173. Auth still uses the JSESSIONID cookie; the Vite proxy keeps it same-origin, so nothing special is needed.

To skip the frontend npm build during a backend-only loop:

```bash
mvn test -DskipFrontend=true
```

---

## Testing

The project has an automated JUnit 5 suite covering all 18 user stories:

* **Unit tests** — Mockito-based, no Spring context; business logic and validation rules.
* **Integration tests** — `@SpringBootTest` + MockMvc against an in-memory H2 database; full request → API controller → service → repository flows, including auth boundaries (a guarded route must answer **401 JSON**, never a redirect) and the SPA history fallback.

Plus a **Vitest** suite in `frontend/` covering the API client, pagination, theme toggle, alert auto-dismiss, and route guards.

Run everything:

```bash
mvn test            # backend + frontend (frontend-maven-plugin runs `npm test`)
```

Or each on its own:

```bash
mvn test -DskipFrontend=true    # backend only
cd frontend && npm test          # frontend only
```

The backend suite runs entirely against H2 — no `.env`, network access, or live MySQL instance required.
