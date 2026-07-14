# Project Structure — Pizza & Drinks Ordering System

```
ONLINE_PIZZA_ORDERING/
├── pom.xml
├── README.md
├── SETUP_GUIDE.md
├── PROJECT_STRUCTURE.md
├── DATABASE_SCHEMA.sql
├── POSTMAN_COLLECTION.json
├── .env / .env.example
├── .gitignore
├── Dockerfile
│
├── src/main/
│   ├── java/com/pizza/
│   │   ├── PizzaOrderingApplication.java          # @SpringBootApplication entry point
│   │   │
│   │   ├── api/                                   # REST controllers — the whole HTTP surface
│   │   │   ├── AuthApiController.java             # Customer register, login, logout — US-001, US-002
│   │   │   ├── PizzaApiController.java            # Customer read-only /api/pizzas — US-003
│   │   │   ├── DrinkApiController.java            # Customer read-only /api/drinks (no auth required)
│   │   │   ├── CartApiController.java             # Cart add/view/remove/qty/apply-coupon; also addDrink — US-007, US-008
│   │   │   ├── OrderApiController.java            # Checkout, place, history, edit, cancel, addDrink — US-007, US-009–US-011
│   │   │   ├── AdminApiController.java            # Admin login, logout, dashboard (pizza + drink stats)
│   │   │   ├── AdminPizzaApiController.java       # Admin pizza CRUD /api/admin/pizzas/** — US-004–US-006
│   │   │   ├── AdminDrinkApiController.java       # Admin drink CRUD /api/admin/drinks/** (multipart)
│   │   │   ├── AdminCouponApiController.java      # Admin coupon CRUD /api/admin/coupons/** — US-012–US-014
│   │   │   ├── AdminCustomerApiController.java    # Admin customer view/edit /api/admin/customers/** — US-015, US-016
│   │   │   ├── AdminOrderApiController.java       # Admin order view/status/bulk /api/admin/orders/** — US-017, US-018
│   │   │   ├── ApiExceptionHandler.java           # @RestControllerAdvice → JSON error bodies
│   │   │   ├── ApiMappers.java                    # Entity → response DTO mapping (the only place entities are read for the wire)
│   │   │   ├── SessionKeys.java                   # Session attribute key constants
│   │   │   └── dto/
│   │   │       └── ApiResponses.java              # All response record types (Envelope, PizzaResponse, DrinkResponse,
│   │   │                                          #   CartItemResponse, OrderItemResponse, OrderResponse, CustomerResponse,
│   │   │                                          #   AdminResponse, MeResponse, DashboardStatsResponse, LoginResponse, …)
│   │   │
│   │   ├── service/
│   │   │   ├── CustomerService.java               # US-001, US-002
│   │   │   ├── AdminService.java                  # Admin authentication
│   │   │   ├── PizzaService.java                  # US-003–US-006; search, sort, Cloudinary CRUD
│   │   │   ├── DrinkService.java                  # Drink browse/search/sort + admin CRUD with Cloudinary;
│   │   │   │                                      #   exposes PREDEFINED_CATEGORIES list; countAll/Available/OutOfStock
│   │   │   ├── CartService.java                   # Cart line-item management; addPizzaToCart, addDrinkToCart — US-007
│   │   │   ├── CouponService.java                 # US-008, US-012–US-014; validateCoupon, findActiveCoupons
│   │   │   ├── OrderService.java                  # Checkout, totals, 5-min edit window, cancel, reorder;
│   │   │   │                                      #   addPizzaToOrder, addDrinkToOrder — US-007, US-009–US-011
│   │   │   ├── CloudinaryService.java             # Image upload / delete / replace; UploadResult record
│   │   │   ├── AdminCustomerService.java          # US-015, US-016
│   │   │   └── AdminOrderService.java             # US-017, US-018; status transitions, bulk-status
│   │   │
│   │   ├── repository/
│   │   │   ├── CustomerRepository.java
│   │   │   ├── AdminRepository.java
│   │   │   ├── PizzaRepository.java               # findByCategory, findByNameContaining, findByAvailableTrue
│   │   │   ├── DrinkRepository.java               # findByCategory, findByNameContaining, findByCategoryAndName, findByAvailableTrue
│   │   │   ├── OrderRepository.java               # findAllOrdered(), findByIdWithDetails(), findByOrderNumber…
│   │   │   ├── OrderItemRepository.java
│   │   │   ├── CartRepository.java
│   │   │   ├── CartItemRepository.java
│   │   │   └── CouponRepository.java
│   │   │
│   │   ├── entity/
│   │   │   ├── Customer.java
│   │   │   ├── Admin.java
│   │   │   ├── Pizza.java                         # name, category, price, imageUrl, imagePublicId, available; Lombok + Builder
│   │   │   ├── Drink.java                         # name, category, price, size, description, imageUrl, imagePublicId, available;
│   │   │   │                                      #   Lombok + Builder; @Table(name="drinks")
│   │   │   ├── Order.java                         # status is a plain String column; holds List<OrderItem>
│   │   │   ├── OrderItem.java                     # pizza (nullable) + drink (nullable) FK; itemType inferred at mapping time
│   │   │   ├── OrderStatus.java                   # enum + canTransitionTo() guard; used by admin order management
│   │   │   ├── Cart.java                          # one cart per customer (username key); holds List<CartItem>
│   │   │   ├── CartItem.java                      # pizza (nullable) + drink (nullable) FK;
│   │   │   │                                      #   getItemTotal() / getUnitPrice() handle both types
│   │   │   └── Coupon.java
│   │   │
│   │   ├── dto/                                   # Form-backing / request DTOs (validated with Jakarta Bean Validation)
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── PizzaDTO.java
│   │   │   ├── DrinkDTO.java                      # name, description, category, price, size, imageUrl, available
│   │   │   ├── OrderDTO.java                      # deliveryAddress, phone, couponCode
│   │   │   ├── CouponDTO.java
│   │   │   └── CustomerUpdateDTO.java             # no password field
│   │   │
│   │   ├── config/
│   │   │   ├── PasswordConfig.java                # BCrypt bean
│   │   │   ├── CloudinaryConfig.java              # tolerates blank credentials without crashing at startup
│   │   │   ├── AdminInitializer.java              # seeds first admin when zero admins exist (env vars required)
│   │   │   ├── DrinkInitializer.java              # seeds 22 default drinks on startup when drinks table is empty;
│   │   │   │                                      #   covers Soft Drinks, Diet, Zero Sugar, Juices, Coffee, Tea,
│   │   │   │                                      #   Energy Drinks, Milkshakes, Water categories
│   │   │   ├── AdminAuthInterceptor.java          # guards /api/admin/** (excludes /api/admin/login, /api/admin/logout)
│   │   │   ├── CustomerAuthInterceptor.java       # guards /api/cart/**, /api/orders/**
│   │   │   ├── SpaForwardingConfig.java           # SPA fallback: non-API, non-asset paths → index.html
│   │   │   └── WebMvcConfig.java                  # registers the two interceptors above
│   │   │
│   │   ├── exception/
│   │   │   ├── ResourceNotFoundException.java     # 404; used by Pizza/Drink/Order/Customer services
│   │   │   ├── DuplicateEmailException.java
│   │   │   ├── DuplicatePhoneException.java
│   │   │   ├── InvalidCredentialsException.java
│   │   │   └── CloudinaryException.java
│   │   │
│   │   └── util/
│   │       └── SessionUtil.java                   # currentCustomer / currentAdmin session helpers
│   │
│   └── resources/
│       ├── application.properties
│       ├── application.properties.example
│       └── static/                                # Vite build output lands here (via Maven package)
│
└── frontend/                                      # React SPA — the entire user interface
    ├── index.html                                 # Shell; carries the pre-paint dark-mode script
    ├── vite.config.js                             # /api proxy (dev), build → target/classes/static
    ├── package.json                               # React 18, React Router v6, Bootstrap 5, Vite 5, Vitest
    └── src/
        ├── main.jsx                               # Bootstrap CSS/JS, context providers, BrowserRouter
        ├── App.jsx                                # Route table; ApiBridge turns 401 into a redirect
        │
        ├── api/
        │   ├── client.js                          # fetch wrapper: credentials, ApiError, 401 hook
        │   └── index.js                           # auth / pizzas / drinks / cart / orders / admin endpoint groups
        │
        ├── context/
        │   ├── SessionContext.jsx                 # customer + admin state; cart item count badge
        │   └── AlertContext.jsx                   # flash-style alerts (success / warning / error)
        │
        ├── hooks/
        │   ├── useApi.js                          # data-fetching hook with loading / error state
        │   ├── usePagination.js                   # client-side page slice
        │   └── useTheme.js                        # dark/light mode toggle, persists to localStorage
        │
        ├── lib/
        │   └── format.js                          # formatMoney, formatDateTime, abbreviate helpers
        │
        ├── styles/
        │   └── styles.css                         # custom overrides on top of Bootstrap
        │
        ├── components/
        │   ├── layout/
        │   │   ├── CustomerLayout.jsx             # navbar (with Drinks link + cart badge) + alerts + footer
        │   │   └── AdminLayout.jsx                # topbar + sidebar (with Drinks entry) + offcanvas mobile
        │   ├── AlertStack.jsx                     # renders queued alerts from AlertContext
        │   ├── Guards.jsx                         # RequireCustomer / RequireAdmin route guards
        │   ├── OrderStatusStepper.jsx             # visual status pipeline
        │   ├── Pagination.jsx                     # shared pager component
        │   ├── StatusBadge.jsx                    # coloured badge for order status
        │   ├── SubmitButton.jsx                   # spinner-aware submit button
        │   └── ThemeToggle.jsx                    # dark/light toggle button
        │
        ├── pages/                                 # Customer-facing screens
        │   ├── Home.jsx                           # featured pizzas strip + hero section
        │   ├── Login.jsx
        │   ├── Register.jsx
        │   ├── PizzaList.jsx                      # search, category filter, price sort, pagination
        │   ├── DrinkList.jsx                      # search, category filter (10 categories), price sort, pagination
        │   ├── Cart.jsx                           # mixed pizza + drink items; coupon apply/remove; totals
        │   ├── Checkout.jsx                       # delivery details review before placing order
        │   ├── OrderSuccess.jsx                   # confirmation screen by order number
        │   ├── OrderHistory.jsx                   # list of past orders with status; reorder / cancel actions
        │   ├── EditOrder.jsx                      # 5-minute edit window; add pizza/drink, adjust qty
        │   └── NotFound.jsx
        │
        └── pages/admin/                           # Admin-facing screens (all behind RequireAdmin guard)
            ├── AdminLogin.jsx
            ├── Dashboard.jsx                      # stats cards: total/available/out-of-stock for pizzas AND drinks
            ├── AdminPizzaList.jsx                 # search, category filter, sort; add/edit/delete actions
            ├── PizzaForm.jsx                      # add / edit pizza with Cloudinary image upload
            ├── AdminDrinkList.jsx                 # search, category filter, sort; add/edit/delete actions
            ├── DrinkForm.jsx                      # add / edit drink with size field + Cloudinary image upload
            ├── CouponList.jsx
            ├── CouponForm.jsx
            ├── CustomerList.jsx
            ├── CustomerForm.jsx
            ├── AdminOrderList.jsx                 # search, status filter, sort; bulk status update
            └── AdminOrderDetail.jsx               # full order detail; status transition controls
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
(`SpaForwardingConfig`), so a hard refresh on a client route like `/admin/drinks/edit/3` works.
Those paths are deliberately **not** interceptor-guarded — the shell must load before it can
ask who the visitor is; the data behind it is guarded, which is what actually matters.

External services:
- **Cloudinary** — pizza and drink image storage (folder `pizza-ordering/pizzas`)

Authentication is fully local (BCrypt + `HttpSession`); no Firebase, JWT, OTP, or Spring Security.

**Note on interceptor coverage:** every data-returning route is guarded. `/api/cart/**` and
`/api/orders/**` require a customer session, `/api/admin/**` requires an admin session, and
cart/order item lookups are additionally scoped to the caller's own rows in the service layer.

---

## React Router Map (client-side routes)

| Path | Component | Auth |
|------|-----------|------|
| `/` | `Home` | Public |
| `/login` | `Login` | Public |
| `/register` | `Register` | Public |
| `/pizzas` | `PizzaList` | Public |
| `/drinks` | `DrinkList` | Public |
| `/cart` | `Cart` | Customer |
| `/checkout` | `Checkout` | Customer |
| `/orders/success/:orderNumber` | `OrderSuccess` | Customer |
| `/orders/history` | `OrderHistory` | Customer |
| `/orders/edit/:orderId` | `EditOrder` | Customer |
| `/admin/login` | `AdminLogin` | Public |
| `/admin/dashboard` | `Dashboard` | Admin |
| `/admin/pizzas` | `AdminPizzaList` | Admin |
| `/admin/pizzas/add` | `PizzaForm` | Admin |
| `/admin/pizzas/edit/:id` | `PizzaForm` | Admin |
| `/admin/drinks` | `AdminDrinkList` | Admin |
| `/admin/drinks/add` | `DrinkForm` | Admin |
| `/admin/drinks/edit/:id` | `DrinkForm` | Admin |
| `/admin/coupons` | `CouponList` | Admin |
| `/admin/coupons/add` | `CouponForm` | Admin |
| `/admin/coupons/edit/:id` | `CouponForm` | Admin |
| `/admin/customers` | `CustomerList` | Admin |
| `/admin/customers/edit/:id` | `CustomerForm` | Admin |
| `/admin/orders` | `AdminOrderList` | Admin |
| `/admin/orders/:id` | `AdminOrderDetail` | Admin |

---

## REST API Route Map

Every server route is JSON under `/api/**`. Everything else is a React Router path served
from the SPA shell.

### Public (no session required)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/me` | Session bootstrap. 200 with nulls when logged out — **not** 401 |
| GET | `/api/pizzas?search=&category=&sort=` | Pizza catalogue — US-003 |
| GET | `/api/drinks?search=&category=&sort=` | Drink catalogue (available drinks only) |
| GET | `/api/drinks/categories` | Predefined drink category list |
| GET | `/api/drinks/{id}` | Single drink by ID |
| POST | `/api/auth/register` | Customer registration — US-001 |
| POST | `/api/auth/login` | Customer login — US-002 |
| POST | `/api/auth/logout` | Clears the customer principal and the applied coupon |
| POST | `/api/admin/login` | Admin login |
| POST | `/api/admin/logout` | Admin logout |

### Customer only (`currentCustomer`; 401 JSON otherwise)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/cart` | Cart, totals, applied + available coupons |
| POST | `/api/cart/items` | Add a pizza to cart |
| POST | `/api/cart/drinks` | Add a drink to cart |
| DELETE | `/api/cart/items/{id}` | Remove a line item |
| POST | `/api/cart/items/{id}/increase` · `/decrease` | Quantity adjustment |
| POST · DELETE | `/api/cart/coupon` | Apply / remove a coupon |
| POST | `/api/orders` | Place the cart as an order — US-007 |
| GET | `/api/orders` | Order history — US-009 |
| GET | `/api/orders/by-number/{orderNumber}` | Confirmation screen |
| GET | `/api/orders/{id}` | Edit screen; **409** once the 5-minute window closes — US-010 |
| PUT | `/api/orders/{id}` | Update delivery address / phone |
| POST | `/api/orders/{id}/items` | Add a pizza to an order in its edit window |
| POST | `/api/orders/{id}/drinks` | Add a drink to an order in its edit window |
| POST | `/api/orders/{id}/items/{itemId}/increase` · `/decrease` | Quantity |
| DELETE | `/api/orders/{id}/items/{itemId}` | Remove an item |
| POST | `/api/orders/{id}/cancel` | Cancel — 409 if not PLACED |
| POST | `/api/orders/{id}/reorder` | Merge a past order back into the cart |

Every cart mutation returns the whole refreshed cart, so the page and the navbar badge
re-render from the mutation's own response without a follow-up GET.

### Admin only (`currentAdmin`; 401 JSON otherwise)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/dashboard` | Pizza + drink counts (total / available / out-of-stock) |
| GET | `/api/admin/pizzas?search=&category=&sort=` | Manage pizzas |
| GET | `/api/admin/pizzas/{id}` | One pizza |
| POST | `/api/admin/pizzas` | Add — **multipart** (Cloudinary) — US-004 |
| POST | `/api/admin/pizzas/{id}` | Update — multipart, image optional — US-005 |
| DELETE | `/api/admin/pizzas/{id}` | Delete — 409 if already ordered — US-006 |
| GET | `/api/admin/drinks?search=&category=&sort=` | Manage drinks (all, incl. unavailable) |
| GET | `/api/admin/drinks/{id}` | One drink |
| POST | `/api/admin/drinks` | Add drink — **multipart** (image required) |
| POST | `/api/admin/drinks/{id}` | Update drink — multipart, image optional |
| DELETE | `/api/admin/drinks/{id}` | Delete drink |
| GET · POST | `/api/admin/coupons` | List / create |
| GET · PUT · DELETE | `/api/admin/coupons/{id}` | Read / update / delete |
| GET | `/api/admin/customers?search=&sort=` | Manage customers — US-016 |
| GET · PUT | `/api/admin/customers/{id}` | Read / update (never the password) |
| GET | `/api/admin/orders?search=&status=&sort=` | Manage orders |
| GET | `/api/admin/orders/{id}` | Detail, incl. `allowedNextStatuses` |
| POST | `/api/admin/orders/{id}/status` | Transition — 409 if illegal — US-018 |
| POST | `/api/admin/orders/bulk-status` | Bulk transition; partial success answers `messageType: "warning"` |

---

## Response Shapes

Mutations answer `{message, messageType, data}` — `messageType` is `success` / `warning` / `error`.

Errors answer `{status, error, message, fieldErrors?}`. `fieldErrors` is keyed by DTO property
name, so a form renders inline validation messages per field.

Entities are never serialized directly. `com.pizza.api.dto.ApiResponses` is the whole wire
contract; `ApiMappers` is the only place entities are read for it.

Key response records defined in `ApiResponses.java`:

| Record | Contents |
|--------|----------|
| `Envelope<T>` | `message`, `messageType`, `data` |
| `ApiError` | `status`, `error`, `message`, `fieldErrors?` |
| `PizzaResponse` | id, name, description, category, price, imageUrl, available |
| `PizzaListResponse` | `pizzas[]`, `categories[]` |
| `DrinkResponse` | id, name, description, category, price, imageUrl, **size**, available |
| `DrinkListResponse` | `drinks[]`, `categories[]` |
| `CartItemResponse` | id, **itemType** ("PIZZA"/"DRINK"), pizzaId/pizzaName/pizzaImageUrl or drinkId/drinkName/drinkImageUrl/drinkCategory/drinkSize, unitPrice, quantity, itemTotal |
| `CartResponse` | items[], subtotal, discount, grandTotal, appliedCoupon, activeCoupons[], itemCount |
| `OrderItemResponse` | id, **itemType** ("PIZZA"/"DRINK"), pizza fields or drink fields, price, quantity, lineTotal |
| `OrderResponse` | id, orderNumber, status, stepIndex, estimatedWindow, allowedNextStatuses, orderTime, subtotal, discountAmount, discountPercentage, couponCode, tax, totalAmount, deliveryAddress, phone, items[], customer, cancellable, editable |
| `CustomerResponse` | id, firstName, lastName, fullName, email, phone, address, createdAt (no password) |
| `AdminResponse` | id, name, email |
| `MeResponse` | customer, admin, cartItemCount |
| `DashboardStatsResponse` | totalPizzas, availablePizzas, outOfStockPizzas, **totalDrinks, availableDrinks, outOfStockDrinks** |
| `LoginResponse` | customer, cartItemCount |

---

## Session Keys

| Key | Principal | Cleared by |
|-----|-----------|------------|
| `currentCustomer` | `Customer` entity | `POST /api/auth/logout` |
| `currentAdmin` | `Admin` entity | `POST /api/admin/logout` |
| `appliedCoupon` | `Coupon` entity | Order placed / coupon removed / customer logout |

Sessions are independent; logging in as one does not affect the other.

---

## Order Status Lifecycle (US-018)

`Order.status` remains a plain `String` column for backward compatibility, but admin transitions
are governed by the `OrderStatus` enum's `canTransitionTo()` guard:

```
PLACED → PROCESSING → OUT_FOR_DELIVERY → DELIVERED   (terminal)
PLACED → CANCELLED
PROCESSING → CANCELLED
```

`DELIVERED` and `CANCELLED` are both terminal — no further transitions are allowed from either.

---

## Drink Categories (Predefined)

`DrinkService.PREDEFINED_CATEGORIES` defines the fixed set used across the API and admin form:

```
Soft Drinks, Diet Drinks, Sugar-Free Drinks, Zero Sugar,
Juices, Coffee, Tea, Energy Drinks, Milkshakes, Water
```

`DrinkInitializer` seeds 22 default drinks covering all categories on first startup (when the
`drinks` table is empty). Admins can add, edit, or delete drinks freely after that.
