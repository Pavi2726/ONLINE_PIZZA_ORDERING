# Online Pizza Ordering System

An Online Pizza Ordering System built using **Spring Boot**, **React**, and **MySQL**. The application enables customers to browse pizzas, place orders, apply coupons, manage their orders, and allows administrators to manage pizzas, customers, orders, and coupons.

The frontend is a React single-page app in [`frontend/`](frontend/) talking to a JSON API under `/api/**`. Maven builds it into the Spring Boot jar, so the whole application still ships and runs as one artifact on port 8080.

---

## Features

### Customer Features

* Customer Registration
* Secure Login & Logout
* Browse Pizza Catalogue
* Search and Filter Pizzas
* Place Pizza Orders
* Apply Coupons
* View Order History
* Update Pending Orders
* Cancel Orders

### Admin Features

* Pizza Management (Add, Update, Delete)
* Coupon Management
* Customer Management
* Order Management

---

## Technology Stack

| Layer          | Technology                  |
| -------------- | --------------------------- |
| Backend        | Java 17, Spring Boot 3.x    |
| Frontend       | React 18, React Router, Bootstrap 5 |
| Frontend build | Vite (via frontend-maven-plugin) |
| Database       | MySQL                       |
| ORM            | Spring Data JPA (Hibernate) |
| Authentication | BCrypt + HttpSession (JSESSIONID) |
| Build Tool     | Maven                       |
| Testing        | JUnit 5, Vitest             |

---

## User Stories

| ID     | Description           |
| ------ | --------------------- |
| US-001 | Customer Registration |
| US-002 | Customer Login        |
| US-003 | View Pizza List       |
| US-004 | Add Pizza             |
| US-005 | Update Pizza          |
| US-006 | Delete Pizza          |
| US-007 | Place Order           |
| US-008 | Apply Coupon          |
| US-009 | View Order History    |
| US-010 | Update Order          |
| US-011 | Cancel Order          |
| US-012 | Create Coupon         |
| US-013 | Update Coupon         |
| US-014 | Delete Coupon         |
| US-015 | View Customers        |
| US-016 | Manage Customers      |
| US-017 | View Orders           |
| US-018 | Manage Orders         |

---

## Project Structure

```text
src/
├── main/
│   ├── java/com/pizza/
│   │   ├── api/          REST controllers + response DTOs (the whole HTTP surface)
│   │   ├── service/      business logic
│   │   ├── repository/   Spring Data JPA
│   │   ├── entity/       JPA entities
│   │   └── config/       auth interceptors, SPA fallback
│   └── resources/
└── test/

frontend/                 React SPA (Vite)
├── src/
│   ├── api/              fetch client + endpoint modules
│   ├── components/       layouts and shared UI
│   ├── context/          session + alerts
│   ├── hooks/            pagination, theme, fetch, submit
│   ├── pages/            customer screens
│   └── pages/admin/      admin screens
└── index.html

DATABASE_SCHEMA.sql
PROJECT_STRUCTURE.md
SETUP_GUIDE.md
pom.xml
```

---

## Getting Started

### Prerequisites

* Java 17 or later
* Maven
* MySQL

### Setup

1. Clone the repository.
2. Configure the environment variables using `.env.example`.
3. Create the database using `DATABASE_SCHEMA.sql`.
4. Update the database configuration.
5. Run the application:

```bash
mvn spring-boot:run
```

The application will be available at:

```
http://localhost:8080
```

---

## Documentation

* **SETUP_GUIDE.md** – Project setup instructions
* **PROJECT_STRUCTURE.md** – Project architecture
* **DATABASE_SCHEMA.sql** – Database schema
* **POSTMAN_COLLECTION.json** – API collection for testing
* **.env.example** – Environment variable template

---

## Development

The application follows a layered architecture consisting of:

* Model
* Repository
* Service
* REST controller (`com.pizza.api`) — thin, delegates to the services
* View (React SPA in `frontend/`)

### Running the frontend with hot reload

`mvn spring-boot:run` serves the last built SPA, which is fine for a quick look but means
re-running Maven after every frontend edit. While working on the UI, run the two together:

```bash
mvn spring-boot:run                  # API on :8080
cd frontend && npm run dev           # SPA on :5173, proxies /api to :8080
```

Open http://localhost:5173. Auth still uses the JSESSIONID cookie; the Vite proxy keeps it
same-origin, so nothing special is needed.

To skip the npm build during a backend-only loop: `mvn test -DskipFrontend=true`.

---

## Testing

The project has an automated JUnit 5 suite covering all 18 user stories:

* **Unit tests** — Mockito-based, no Spring context; business logic and validation rules.
* **Integration tests** — `@SpringBootTest` + MockMvc against an in-memory H2 database; full request → API controller → service → repository flows, including the auth boundaries (a guarded API route must answer **401 JSON**, never a redirect) and the SPA history fallback.

Plus a small **Vitest** suite in `frontend/` for the logic ported out of the old vanilla JS — the API client, pagination, the theme toggle, alert auto-dismiss, and the route guards.

Run everything with:

```bash
mvn test            # backend + frontend (frontend-maven-plugin runs `npm test`)
```

Or each on its own:

```bash
mvn test -DskipFrontend=true
cd frontend && npm test
```

The backend suite runs entirely against H2 and never touches the live MySQL database configured in `.env` — no additional setup is required to run it.

---

