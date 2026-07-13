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
│       └── static/                         # Vite build output lands here (target/classes/static)
│
└── frontend/                                # React SPA — the entire user interface
    ├── index.html                           # Shell; carries the pre-paint dark-mode script
    ├── vite.config.js                       # /api proxy (dev), build → target/classes/static
    └── src/
        ├── main.jsx                         # Bootstrap CSS/JS + providers + router
        ├── App.jsx                          # Route table; ApiBridge turns a 401 into a redirect
        ├── api/
        │   ├── client.js                    # fetch wrapper: credentials, ApiError, 401 hook
        │   └── index.js                     # auth / pizzas / cart / orders / admin endpoints
        ├── context/
        │   ├── SessionContext.jsx           # successor to GlobalModelAdvice
        │   └── AlertContext.jsx             # successor to the flash attributes
        ├── hooks/                           # useApi, useSubmit, usePagination, useTheme
        ├── components/
        │   ├── layout/CustomerLayout.jsx    # navbar + alerts + footer
        │   ├── layout/AdminLayout.jsx       # topbar + sidebar + offcanvas
        │   ├── AlertStack.jsx  Guards.jsx  Pagination.jsx  SubmitButton.jsx
        │   └── OrderStatusStepper.jsx  StatusBadge.jsx
        ├── lib/format.js                    # money / dateTime / abbreviate
        ├── pages/                           # Home, Login, Register, PizzaList, Cart,
        │                                    # Checkout, OrderSuccess, OrderHistory, EditOrder
        └── pages/admin/                     # Dashboard, pizzas, coupons, customers, orders
```

---

## Architecture

```
React SPA (browser)  ── fetch, JSESSIONID cookie ──┐
                                                    ↓
@RestController (com.pizza.api)  ← interceptors: 401 JSON on /api/admin/**, /api/cart/**, /api/orders/**
        ↓
Service     ← @Transactional business logic
        ↓
Repository  ← Spring Data JPA
        ↓
Aiven MySQL (production) / H2 in-memory (test suite)
```

Anything that is not a real static file and not under `/api/**` is served the SPA shell
(`SpaForwardingConfig`), so a hard refresh on a client route like `/admin/orders/5` works.
Those paths are deliberately **not** interceptor-guarded — the shell must load before it can
ask who the visitor is; the data behind it is guarded, which is what actually matters.

External services:
- **Cloudinary** — pizza image storage

Authentication is fully local (BCrypt + `HttpSession`); no external auth provider, no Spring Security.

**Note on interceptor coverage:** every data-returning route is guarded. `/api/cart/**` and
`/api/orders/**` require a customer, `/api/admin/**` requires an admin, and cart/order item
lookups are additionally scoped to the caller's own rows in the service layer, so one
customer cannot reach another's cart item by id (see `CartOwnershipIdorIntegrationTest`).

---

## Route Map

Every server route is JSON under `/api/**`. Everything else is a React Router path served
from the SPA shell.

### Public (no session required)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/me` | Session bootstrap. 200 with nulls when logged out — **not** 401 |
| GET | `/api/pizzas?search=&category=&sort=` | Catalogue — US-003 (also backs the home page's featured strip) |
| POST | `/api/auth/register` | Customer registration — US-001 |
| POST | `/api/auth/login` | Customer login — US-002 |
| POST | `/api/auth/logout` | Clears the principal **and** the applied coupon |
| POST | `/api/admin/login` | Admin login |
| POST | `/api/admin/logout` | Admin logout |

### Customer only (`currentCustomer`; 401 JSON otherwise)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/cart` | Cart, totals, applied + available coupons. Also backs the checkout screen |
| POST | `/api/cart/items` | Add a pizza |
| DELETE | `/api/cart/items/{id}` | Remove a line |
| POST | `/api/cart/items/{id}/increase` · `/decrease` | Quantity |
| POST · DELETE | `/api/cart/coupon` | Apply / remove a coupon |
| POST | `/api/orders` | Place the cart as an order — US-007 |
| GET | `/api/orders` | Order history — US-009 |
| GET | `/api/orders/by-number/{orderNumber}` | Confirmation screen |
| GET | `/api/orders/{id}` | Edit screen; **409** once the 5-minute window closes — US-010 |
| PUT | `/api/orders/{id}` | Update delivery address / phone |
| POST | `/api/orders/{id}/items` | Add a pizza to an order still in its window |
| POST | `/api/orders/{id}/items/{itemId}/increase` · `/decrease` | Quantity |
| DELETE | `/api/orders/{id}/items/{itemId}` | Remove an item |
| POST | `/api/orders/{id}/cancel` | Cancel — 409 if not PLACED |
| POST | `/api/orders/{id}/reorder` | Merge a past order back into the cart |

Every cart mutation returns the whole refreshed cart, so the page and the navbar badge
re-render from the mutation's own response without a follow-up GET.

### Admin only (`currentAdmin`; 401 JSON otherwise)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/dashboard` | Pizza counts |
| GET | `/api/admin/pizzas?search=&category=&sort=` | Manage pizzas |
| GET | `/api/admin/pizzas/{id}` | One pizza |
| POST | `/api/admin/pizzas` | Add — **multipart** (Cloudinary) — US-004 |
| POST | `/api/admin/pizzas/{id}` | Update — multipart, image optional. POST not PUT: multipart-on-PUT is unreliable — US-005 |
| DELETE | `/api/admin/pizzas/{id}` | Delete — 409 if already ordered — US-006 |
| GET · POST | `/api/admin/coupons` | List / create |
| GET · PUT · DELETE | `/api/admin/coupons/{id}` | Read / update / delete |
| GET | `/api/admin/customers?search=&sort=` | Manage customers — US-016 |
| GET · PUT | `/api/admin/customers/{id}` | Read / update (never the password) |
| GET | `/api/admin/orders?search=&status=&sort=` | Manage orders |
| GET | `/api/admin/orders/{id}` | Detail, incl. `allowedNextStatuses` |
| POST | `/api/admin/orders/{id}/status` | Transition — 409 if illegal — US-018 |
| POST | `/api/admin/orders/bulk-status` | Bulk transition; partial success answers `messageType: "warning"` |

### Response shapes
Mutations answer `{message, messageType, data}` — `messageType` is `success` / `warning` /
`error`, the direct successor of the old flash attributes, and the client renders the same
three Bootstrap alert styles from it.

Errors answer `{status, error, message, fieldErrors?}`. `fieldErrors` is keyed by DTO
property name, so a form renders `is-invalid` + `.invalid-feedback` per field exactly as
Thymeleaf's `th:errors` did.

Entities are never serialized: `Order.customer` is LAZY with `open-in-view=false`,
`Cart`/`Order` hold bidirectional collections Jackson would recurse through, and
`Customer.password` carries the BCrypt hash. `com.pizza.api.dto.ApiResponses` is the whole
wire contract; `ApiMappers` is the only place entities are read for it.

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
