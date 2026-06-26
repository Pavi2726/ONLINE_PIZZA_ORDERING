# Online Pizza Ordering System

An Online Pizza Ordering System built using **Spring Boot**, **Thymeleaf**, and **MySQL**. The application enables customers to browse pizzas, place orders, apply coupons, manage their orders, and allows administrators to manage pizzas, customers, orders, and coupons.

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
| Frontend       | Thymeleaf, Bootstrap 5      |
| Database       | MySQL                       |
| ORM            | Spring Data JPA (Hibernate) |
| Authentication | BCrypt + HttpSession        |
| Build Tool     | Maven                       |
| Testing        | JUnit 5                     |

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
│   ├── java/
│   ├── resources/
│   └── templates/
└── test/

DATABASE_SCHEMA.sql
PROJECT_STRUCTURE.md
SETUP_GUIDE.md
POSTMAN_COLLECTION.json
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
* Controller
* View (Thymeleaf)

Development follows the **Test Driven Development (TDD)** approach with unit and integration testing.

---

