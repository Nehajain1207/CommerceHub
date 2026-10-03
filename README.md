# CommerceHub

A small online store, front end and back end: customers browse products, fill a cart and place orders; the shop owner manages products, stock and orders.

It is built around one problem: **when only a few units are left and many people order at the same moment, the shop must never sell more than it has.**

React + TypeScript (Vite) · Java 17 · Spring Boot 3.5 · PostgreSQL · Spring Security (JWT) · Docker

> Phase 1 of the project. Planned next: order events with Kafka, a load test, Kubernetes.

## What it does

**Customers**
- Browse products with search, category filter, sorting and pages
- See how much stock is left on every product
- Add to cart, change quantities, place an order, cancel it before it ships
- See their own orders and each order's status

**Shop owner (admin)**
- Add and edit products, hide them from the shop
- Correct stock up or down, see which products are running low
- See every order and move it from placed to shipped to delivered

## How overselling is prevented

Stock is never changed by "read the number, change it in Java, save it back", because two orders doing that at the same moment overwrite each other. Instead, checkout runs one database statement per product:

```sql
UPDATE products SET stock = stock - :quantity
WHERE id = :productId AND stock >= :quantity
```

The database runs this as a single step. If it changes one row, the stock was reserved. If it changes none, there was not enough, the order is refused and everything the order had reserved so far is rolled back. Stock can never go below zero.

Other details that follow from the same idea:
- Cancelling an order first flips its status from PLACED to CANCELLED in one statement, and only the request that made that change gives the stock back. Two cancel requests cannot return the stock twice.
- Editing a product's name or price never writes the stock column, so it cannot undo an order placed in between.
- An order stores the name and price at the time of purchase, so later price changes do not rewrite it.

## Run it

Needs Java 17+, Node.js 18+ and Docker.

```bash
# 1. database
docker compose up -d

# 2. back end  (http://localhost:8090)
cd backend
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run

# 3. front end (http://localhost:5173), in a second terminal
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. On first start the back end creates sample products and two demo accounts for local use:

| Account | Email | Password |
|---|---|---|
| Admin | `admin@commercehub.local` | `admin123` |
| Customer | `customer@commercehub.local` | `customer123` |

API documentation: http://localhost:8090/swagger-ui.html

## Tests

```bash
cd backend
./mvnw test
```

The tests use an in-memory database, so they need no Docker. They cover checkout, cancellation, order status rules, sign-up and login, product management and the cart, plus one concurrency test: 50 customers check out at the same instant for a product with 5 units, and exactly 5 orders must succeed.

## Project layout

```
backend/    Spring Boot API
  security/   login tokens (JWT) and who may call what
  user/       sign-up and login
  catalog/    products, categories, stock
  cart/       the customer's cart
  order/      checkout, cancellation, order status
  common/     error handling shared by all of the above
frontend/   React app
  src/api.ts        every call to the back end
  src/session.tsx   who is logged in, cart count
  src/pages/        one file per screen
  src/components/   pieces shared between screens
```

## Configuration

No secrets are stored in the code. The defaults are for local development only.

| Variable | Purpose | Local default |
|---|---|---|
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `JWT_SECRET` | signing key for login tokens, at least 32 characters | a dev-only value |
| `ADMIN_PASSWORD` | password of the demo admin account | `admin123` |

## Known limitations

- No payment step: placing an order completes it.
- Products have no images yet.
- The demo accounts and sample data are for local development and should be switched off (`app.seed.enabled=false`) anywhere else.

## Author

Neha Jain
