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
├── src/main/
│   ├── java/com/pizza/
│   │   ├── PizzaOrderingApplication.java
│   │   │
│   │   ├── controller/
│   │   │   ├── AuthController.java          # Customer register, login, logout — US-001, US-002
│   │   │   ├── CustomerController.java      # Home page
│   │   │   ├── PizzaController.java         # Customer read-only /pizzas — US-003
│   │   │   ├── CartController.java          # Cart add/view/remove/qty/apply-coupon — US-007, US-008
│   │   │   ├── OrderController.java         # Checkout, place, history, edit, cancel — US-007, US-009–US-011
│   │   │   ├── AdminController.java         # Admin login, dashboard, logout
│   │   │   ├── AdminPizzaController.java    # Admin pizza CRUD /admin/pizzas/** — US-004–US-006
│   │   │   ├── AdminCouponController.java   # Admin coupon CRUD /admin/coupons/** — US-012–US-014
│   │   │   ├── AdminCustomerController.java # Admin customer view/edit /admin/customers/** — US-015, US-016
│   │   │   ├── AdminOrderController.java    # Admin order view/status /admin/orders/** — US-017, US-018
│   │   │   └── GlobalModelAdvice.java       # Shared model attributes (currentCustomer/currentAdmin)
│   │   │
│   │   ├── service/
│   │   │   ├── CustomerService.java         # US-001, US-002
│   │   │   ├── AdminService.java            # Admin authentication
│   │   │   ├── PizzaService.java            # US-003–US-006
│   │   │   ├── CartService.java             # Cart line-item management — US-007
│   │   │   ├── CouponService.java           # US-008, US-012–US-014
│   │   │   ├── OrderService.java            # Checkout, totals, edit window, cancel — US-007, US-009–US-011
│   │   │   ├── CloudinaryService.java       # Image upload/delete/replace
│   │   │   ├── AdminCustomerService.java    # US-015, US-016
│   │   │   └── AdminOrderService.java       # US-017, US-018
│   │   │
│   │   ├── repository/
│   │   │   ├── CustomerRepository.java
│   │   │   ├── AdminRepository.java
│   │   │   ├── PizzaRepository.java
│   │   │   ├── OrderRepository.java         # incl. fetch-joined findAllOrdered(), findByIdWithDetails()
│   │   │   ├── OrderItemRepository.java
│   │   │   ├── CartRepository.java
│   │   │   ├── CartItemRepository.java
│   │   │   └── CouponRepository.java
│   │   │
│   │   ├── entity/
│   │   │   ├── Customer.java
│   │   │   ├── Admin.java
│   │   │   ├── Pizza.java
│   │   │   ├── Order.java                   # status is a String column, not the OrderStatus enum
│   │   │   ├── OrderItem.java
│   │   │   ├── OrderStatus.java             # enum + canTransitionTo() guard, used by admin order management
│   │   │   ├── Cart.java                    # plain getters/setters (no Lombok, unlike the rest)
│   │   │   ├── CartItem.java                # plain getters/setters (no Lombok, unlike the rest)
│   │   │   └── Coupon.java
│   │   │
│   │   ├── dto/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── PizzaDTO.java
│   │   │   ├── OrderDTO.java                # deliveryAddress, phone, couponCode — no pizzaId/quantity
│   │   │   ├── CouponDTO.java
│   │   │   ├── CustomerUpdateDTO.java       # no password field
│   │   │   ├── EditOrderDTO.java            # unused by any controller
│   │   │   └── EditOrderItemDTO.java        # unused by any controller
│   │   │
│   │   ├── config/
│   │   │   ├── PasswordConfig.java          # BCrypt bean
│   │   │   ├── CloudinaryConfig.java        # tolerates blank credentials without crashing at startup
│   │   │   ├── AdminInitializer.java        # optional admin seeding, only when zero admins exist
│   │   │   ├── AdminAuthInterceptor.java    # guards /admin/** (excludes /admin/login, /admin/logout)
│   │   │   ├── CustomerAuthInterceptor.java # guards /orders/** only
│   │   │   └── WebMvcConfig.java            # registers the two interceptors above
│   │   │
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   ├── ResourceNotFoundException.java   # used by Pizza/Order/AdminCustomer/AdminOrder services
│   │   │   ├── DuplicateEmailException.java
│   │   │   ├── DuplicatePhoneException.java
│   │   │   ├── InvalidCredentialsException.java
│   │   │   └── CloudinaryException.java
│   │   │   # Note: CartService/CouponService throw plain RuntimeException instead of
│   │   │   # ResourceNotFoundException for their not-found/invalid cases — an existing
│   │   │   # inconsistency, not a typo in this doc.
│   │   │
│   │   └── util/
│   │       └── SessionUtil.java             # currentCustomer / currentAdmin session-attribute keys
│   │
│   └── resources/
│       ├── application.properties
│       ├── application.properties.example
│       │
│       ├── templates/
│       │   ├── fragments/
│       │   │   ├── layout.html              # Customer navbar/footer/alerts/scripts (th:replace fragments)
│       │   │   └── admin-layout.html        # Admin sidebar/topbar/alerts/scripts
│       │   ├── home.html
│       │   ├── login.html
│       │   ├── register.html
│       │   ├── pizza-list.html              # Customer browse/search/filter/sort/pagination
│       │   ├── cart.html                    # Cart view, qty +/-, remove, apply coupon
│       │   ├── checkout.html                # Order summary + place order (does not use fragments/layout)
│       │   ├── place-order.html             # Legacy single-pizza form — orphaned, not linked anywhere
│       │   ├── order-success.html
│       │   ├── order-history.html           # US-009, status badges for all 5 statuses
│       │   ├── edit-order.html              # US-010 (does not use fragments/layout; no viewport meta tag)
│       │   ├── error.html
│       │   ├── admin-login.html
│       │   ├── admin-dashboard.html
│       │   ├── admin-pizza-list.html
│       │   ├── add-pizza.html
│       │   ├── edit-pizza.html
│       │   ├── admin-coupon-list.html
│       │   ├── add-coupon.html
│       │   ├── edit-coupon.html
│       │   ├── admin-customer-list.html
│       │   ├── edit-customer.html
│       │   ├── admin-order-list.html
│       │   └── admin-order-detail.html
│       │
│       └── static/
│           ├── css/styles.css               # Bootstrap 5.3.3 (CDN) + ~120 lines of custom CSS/tokens
│           └── js/
│               ├── app.js                   # order-total calc, double-submit guard, pizza-grid pagination
│               └── admin-table.js           # admin pizza-table pagination
│
└── src/test/java/com/pizza/                 # JUnit 5 suite — 220 tests, 31 classes (see TESTING.md)
    ├── AbstractIntegrationTest.java         # shared @SpringBootTest + H2 + mocked Cloudinary base
    ├── SmokeTest.java
    ├── testsupport/TestDataFactory.java     # shared entity fixtures for all tests
    ├── service/                             # 8 Mockito unit-test classes
    ├── entity/OrderStatusTest.java          # exhaustive transition-matrix test
    ├── controller/                          # 10 @WebMvcTest classes, one per controller
    └── integration/                         # 11 @SpringBootTest + H2 classes
```

---

## Architecture

```
Browser (Thymeleaf)
        ↓
Controller  ← interceptors (AdminAuth on /admin/**, CustomerAuth on /orders/** only)
        ↓
Service     ← @Transactional business logic
        ↓
Repository  ← Spring Data JPA
        ↓
Aiven MySQL (production) / H2 in-memory (test suite)
```

External services:
- **Cloudinary** — pizza image storage

Authentication is fully local (BCrypt + `HttpSession`); no external auth provider, no Spring Security.

**Note on interceptor coverage:** `/cart/**` (add/view/remove/increase/decrease/apply-coupon) is not covered by either interceptor and has no in-controller session check for the mutation endpoints — this is a known gap, not an intentional public API.

---

## Route Map

### Public (no session required)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | Home |
| GET/POST | `/register` | Customer registration — US-001 |
| GET/POST | `/login` | Customer login — US-002 |
| GET | `/logout` | Customer logout |
| GET | `/pizzas` | Pizza menu, search/filter/sort — US-003 |
| GET/POST | `/admin/login` | Admin login |

### Cart (not session-gated — see note above)
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/cart/add` | Add pizza to cart (checks for a customer session in-controller) |
| GET | `/cart` | View cart (checks for a customer session in-controller) |
| POST | `/cart/remove` | Remove a cart item |
| POST | `/increase/{cartItemId}` | Increase item quantity |
| POST | `/decrease/{cartItemId}` | Decrease item quantity |
| POST | `/cart/apply-coupon` | Validate and store a coupon in session — US-008 |

### Customer only (`currentCustomer` session, enforced on `/orders/**`)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/orders/new` | *Legacy, orphaned — not linked from any page; throws if hit directly* |
| POST | `/orders` | *Legacy, orphaned — same as above* |
| GET | `/orders/checkout` | Checkout summary from cart + coupon — US-007 |
| POST | `/orders/place` | Place order from cart — US-007 |
| GET | `/orders/success/{orderNumber}` | Confirmation |
| GET | `/orders/history` | Order history — US-009 |
| POST | `/orders/cancel/{orderId}` | Cancel own order (only if `PLACED`) — US-011 |
| GET | `/orders/edit/{orderId}` | Edit-order page (5-minute window) — US-010 |
| POST | `/orders/edit/{orderId}/increase/{itemId}` | Increase item qty in a pending order |
| POST | `/orders/edit/{orderId}/decrease/{itemId}` | Decrease item qty (floor 1) |
| POST | `/orders/edit/{orderId}/add-pizza` | Add a new line to a pending order |
| POST | `/orders/edit/{orderId}` | Update delivery address/phone |
| POST | `/orders/edit/{orderId}/remove/{itemId}` | Remove a line item (blocked if it's the last one) |

### Admin only (`currentAdmin` session, enforced on `/admin/**` except `/admin/login`, `/admin/logout`)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/admin` | Redirect to dashboard or login depending on session |
| GET | `/admin/dashboard` | Dashboard stats |
| GET | `/admin/logout` | Admin logout |
| GET | `/admin/pizzas` | Manage pizzas — US-004–US-006 |
| GET/POST | `/admin/pizzas/add` | Add pizza |
| GET/POST | `/admin/pizzas/edit/{id}` | Edit pizza |
| POST | `/admin/pizzas/delete/{id}` | Delete pizza |
| GET | `/admin/coupons` | Manage coupons — US-012–US-014 |
| GET/POST | `/admin/coupons/add` | Create coupon |
| GET | `/admin/coupons/edit/{id}` | Edit coupon form |
| POST | `/admin/coupons/update/{id}` | Update coupon |
| POST | `/admin/coupons/delete/{id}` | Delete coupon |
| GET | `/admin/customers` | View customers — US-015 |
| GET | `/admin/customers/edit/{id}` | Edit customer form — US-016 |
| POST | `/admin/customers/update/{id}` | Update customer |
| GET | `/admin/orders` | View orders — US-017 |
| GET | `/admin/orders/{id}` | Order detail |
| POST | `/admin/orders/{id}/status` | Transition order status — US-018 |

---

## Session Keys

| Key | Principal | Cleared by |
|-----|-----------|------------|
| `currentCustomer` | `Customer` entity | `GET /logout` |
| `currentAdmin` | `Admin` entity | `GET /admin/logout` |

Sessions are independent; logging in as one does not affect the other.

---

## Order status lifecycle (US-018)

`Order.status` remains a plain `String` column for backward compatibility, but admin transitions are governed by the `OrderStatus` enum's `canTransitionTo()` guard:

```
PLACED → PROCESSING → OUT_FOR_DELIVERY → DELIVERED   (terminal)
PLACED → CANCELLED
PROCESSING → CANCELLED
```

`DELIVERED` and `CANCELLED` are both terminal — no further transitions are allowed from either.
