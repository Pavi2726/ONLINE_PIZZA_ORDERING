# Project Structure — Pizza Ordering System

```
ONLINEPIZZAORDERING/
├── pom.xml
├── README.md
├── SETUP_GUIDE.md
├── PROJECT_STRUCTURE.md
├── DATABASE_SCHEMA.sql
├── POSTMAN_COLLECTION.json
├── .env.example
├── .gitignore
│
└── src/main/
    ├── java/com/pizza/
    │   ├── PizzaOrderingApplication.java
    │   │
    │   ├── controller/
    │   │   ├── AuthController.java          # Customer register, login, logout
    │   │   ├── CustomerController.java      # Home page
    │   │   ├── PizzaController.java         # Customer read-only /pizzas
    │   │   ├── OrderController.java         # Customer order placement
    │   │   ├── AdminController.java         # Admin login, dashboard, logout
    │   │   ├── AdminPizzaController.java    # Admin pizza CRUD /admin/pizzas/**
    │   │   └── GlobalModelAdvice.java       # Shared model attributes
    │   │
    │   ├── service/
    │   │   ├── CustomerService.java         # US-001, US-002
    │   │   ├── AdminService.java            # Admin authentication
    │   │   ├── PizzaService.java            # US-003–US-006
    │   │   ├── OrderService.java            # US-007
    │   │   └── CloudinaryService.java       # Image upload/delete/replace
    │   │
    │   ├── repository/
    │   │   ├── CustomerRepository.java
    │   │   ├── AdminRepository.java
    │   │   ├── PizzaRepository.java
    │   │   └── OrderRepository.java
    │   │
    │   ├── entity/
    │   │   ├── Customer.java
    │   │   ├── Admin.java
    │   │   ├── Pizza.java
    │   │   └── Order.java
    │   │
    │   ├── dto/
    │   │   ├── RegisterRequest.java
    │   │   ├── LoginRequest.java
    │   │   ├── PizzaDTO.java
    │   │   └── OrderDTO.java
    │   │
    │   ├── config/
    │   │   ├── PasswordConfig.java          # BCrypt bean
    │   │   ├── CloudinaryConfig.java
    │   │   ├── AdminInitializer.java        # Optional admin seeding
    │   │   ├── AdminAuthInterceptor.java    # Guards /admin/**
    │   │   ├── CustomerAuthInterceptor.java # Guards /orders/**
    │   │   └── WebMvcConfig.java
    │   │
    │   ├── exception/
    │   │   ├── GlobalExceptionHandler.java
    │   │   └── … custom exceptions
    │   │
    │   └── util/
    │       └── SessionUtil.java             # currentCustomer / currentAdmin
    │
    └── resources/
        ├── application.properties
        ├── application.properties.example
        │
        ├── templates/
        │   ├── fragments/
        │   │   ├── layout.html              # Customer navbar/footer
        │   │   └── admin-layout.html        # Admin sidebar/topbar
        │   ├── home.html
        │   ├── login.html
        │   ├── register.html
        │   ├── pizza-list.html              # Customer read-only menu
        │   ├── place-order.html
        │   ├── order-success.html
        │   ├── error.html
        │   ├── admin-login.html
        │   ├── admin-dashboard.html
        │   ├── admin-pizza-list.html
        │   ├── add-pizza.html               # Admin layout
        │   └── edit-pizza.html              # Admin layout
        │
        └── static/
            ├── css/styles.css
            └── js/
                ├── app.js
                └── admin-table.js
```

---

## Architecture

```
Browser (Thymeleaf)
        ↓
Controller  ← interceptors (AdminAuth / CustomerAuth)
        ↓
Service     ← @Transactional business logic
        ↓
Repository  ← Spring Data JPA
        ↓
Aiven MySQL
```

External services:
- **Cloudinary** — pizza image storage

Authentication is fully local (BCrypt + `HttpSession`); no external auth provider.

---

## Route Map

### Public (no session required)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | Home |
| GET/POST | `/register` | Customer registration |
| GET/POST | `/login` | Customer login |
| GET | `/logout` | Customer logout |
| GET | `/pizzas` | Pizza menu (read-only) |
| GET/POST | `/admin/login` | Admin login |

### Customer only (`currentCustomer` session)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/orders/new` | Order form |
| POST | `/orders` | Place order |
| GET | `/orders/success/{orderNumber}` | Confirmation |

### Admin only (`currentAdmin` session)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/admin/dashboard` | Dashboard stats |
| GET | `/admin/logout` | Admin logout |
| GET | `/admin/pizzas` | Manage pizzas |
| GET/POST | `/admin/pizzas/add` | Add pizza |
| GET/POST | `/admin/pizzas/edit/{id}` | Edit pizza |
| POST | `/admin/pizzas/delete/{id}` | Delete pizza |

---

## Session Keys

| Key | Principal | Cleared by |
|-----|-----------|------------|
| `currentCustomer` | `Customer` entity | `GET /logout` |
| `currentAdmin` | `Admin` entity | `GET /admin/logout` |

Sessions are independent; logging in as one does not affect the other.
