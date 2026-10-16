# Chaos test: break things during real traffic and prove nothing is lost

Run this with the whole system in Docker (`docker compose --profile app up -d --build`).
Keep the Grafana dashboard open (http://localhost:3000) to watch it happen.

Commands are for Windows PowerShell, run from the `commercehub` folder.

## 0. Start from clean databases

```
docker compose --profile app down -v
docker compose --profile app up -d --build
```

Wait about a minute, until http://localhost:8080 shows the shop.

## 1. Start steady shopping (terminal 1)

```
k6 run loadtest/k6/checkout-steady.js
```

200 customers keep adding to cart, checking out and paying for 3 minutes.
Wait for the line `Set-up done ... Start breaking things now.` and note the product id it prints.

## 2. Break things (terminal 2), one at a time, about 30 seconds apart

**a. Kafka goes down for 30 seconds**

```
docker stop commercehub_kafka
# wait 30 seconds, watch "Outbox: events waiting for Kafka" climb in Grafana
docker start commercehub_kafka
# the waiting events drain back to 0
```

**b. One back-end copy crashes**

```
docker ps --filter "name=backend" --format "{{.Names}}"
docker kill commercehub-backend-1
# nginx sends all traffic to the surviving copy; "Requests per back-end copy" shows it
docker start commercehub-backend-1
```

**c. The payment provider starts failing** (60% of calls)

```
$env:DEMO_FAILURE_RATE="0.6"; docker compose --profile app up -d backend
# retries hide most failures; if failures continue, the circuit breaker opens ("Payment gateway circuit breaker" panel)
$env:DEMO_FAILURE_RATE="0"; docker compose --profile app up -d backend
```

(This replaces the back-end copies with new ones, like a deploy during traffic. A few requests fail while they restart; nginx finds the new copies by itself.)

## 3. When k6 finishes, check that nothing was lost or doubled

Wait 10 seconds so the last events reach Kafka, then:

```
Get-Content loadtest\verify.sql | docker exec -i commercehub_postgres psql -U postgres -d commercehub -v product_id=PUT_ID_HERE
Get-Content loadtest\verify-notifications.sql | docker exec -i commercehub_notifications_db psql -U postgres -d notifications
```

Pass when:

| Check | Expected |
|---|---|
| `total_accounted_for` | exactly 1,000,000 (the starting stock): no unit lost or sold twice |
| `orders_with_two_payments`, `confirmed_orders_without_payment` | 0 |
| `events_still_waiting` | 0: every event reached Kafka after it came back |
| `notifications_total` | equal to `events_total`: every event delivered exactly once |

Some checkouts fail with errors while a copy is being killed or payments are failing. That is expected (the customer sees "try again"). What must never happen is a wrong number in the table above.
