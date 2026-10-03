# CommerceHub

A small online store, front end and back end: customers browse products, fill a cart and place orders; the shop owner manages products, stock and orders.

It is built around one problem: **when only a few units are left and many people order at the same moment, the shop must never sell more than it has.**

React + TypeScript (Vite) · Java 17 · Spring Boot 3.5 · PostgreSQL · Spring Security (JWT) · Docker

> Phases 1 and 2 are built (store, then payments). Planned next: order events with Kafka, a load test, Kubernetes.

## What it does

**Customers**
- Browse products with search, category filter, sorting and pages
- See how much stock is left on every product
- Add to cart, change quantities, place an order and pay for it
- Come back and pay an order that is still waiting, or cancel it
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

## How payment works

Placing an order is two steps, because an order should not count until it is paid.

1. **Checkout** creates the order as "awaiting payment" and holds the stock. The server works out the amount from the order it just built; the browser never sends a price.
2. **Payment** happens in the payment company's window. When it reports success, the server checks the signature (HMAC-SHA256 with a secret only the server and the payment company know) before marking the order paid. A forged or altered "payment succeeded" message is refused.

Things that can go wrong, and what happens:

| Situation | Result |
|---|---|
| Customer closes the browser right after paying | The payment company's server-to-server notification (webhook) still confirms the order |
| Browser and webhook both report the same payment | Confirmed once; the second report changes nothing |
| Customer never pays | After 15 minutes the order is cancelled and the stock goes back on sale |
| Payment arrives after the order was cancelled | The order stays cancelled and the payment is flagged for refund |
| Webhook reports a different amount than the order | Ignored |

Talking to the payment company is a network call, so it is done outside any database transaction: no product rows stay locked while waiting on the network.

**Two modes.** With Razorpay test keys set (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`), the real Razorpay payment window opens. Without keys the app runs in demo mode: a confirmation box stands in for the payment window, and the same signature check runs behind it. Both modes have been run: an order was paid through Razorpay's test-mode window and confirmed after signature verification. The webhook is covered by automated tests but has not yet received a live call from Razorpay, because that needs a public address.

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

The tests use an in-memory database, so they need no Docker. They cover checkout, payment (genuine and forged signatures, webhooks, repeated confirmations, unpaid orders timing out, late payments), cancellation, order status rules, sign-up and login, product management and the cart, plus one concurrency test: 50 customers check out at the same instant for a product with 5 units, and exactly 5 orders must succeed.

## Project layout

```
backend/    Spring Boot API
  security/   login tokens (JWT) and who may call what
  user/       sign-up and login
  catalog/    products, categories, stock
  cart/       the customer's cart
  order/      orders, cancellation, order status
  payment/    checkout, payment gateway, signature checks, webhook
  common/     error handling shared by all of the above
frontend/   React app
  src/api.ts        every call to the back end
  src/session.tsx   who is logged in, cart count
  src/payment.ts    opens the payment step for an order
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
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | Razorpay test keys | empty (demo mode) |
| `RAZORPAY_WEBHOOK_SECRET` | secret for checking webhook signatures | empty (webhooks refused) |

## Known limitations

- Razorpay is used in test mode only; no real money moves.
- The webhook is tested with simulated notifications, not live ones from Razorpay.
- A paid order cannot be cancelled, because refunds are not built yet.
- Products have no images yet.
- The demo accounts and sample data are for local development and should be switched off (`app.seed.enabled=false`) anywhere else.

## Author

Neha Jain
