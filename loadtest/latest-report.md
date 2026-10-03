# CommerceHub load test report

Run: 2026-10-03 21:27 | customers: 200 | checkout requests: 400

## Scenario A - flash sale (20 units, 1 each)
- orders placed: 20
- told "sold out": 180
- stock left: 0
- unexpected responses: 0

## Scenario B - each order cancelled twice at the same instant
- orders cancelled: 20 of 20
- duplicate cancels refused: 20
- stock after cancels: 20 (started at 20)
- unexpected responses: 0

## Scenario C - mixed rush (50 units, 1 to 3 each)
- orders placed: 32
- told "sold out": 168
- units in placed orders: 50
- units gone from stock: 50
- stock left: 0
- unexpected responses: 0

## Response time (checkout requests, sent at once)
- p50: 1304 ms
- p95: 1999 ms
- max: 2143 ms

## Connections
- connection attempts retried: 0

## Checks
- PASS: flash sale: exactly 20 orders, stock ends at 0
- PASS: double cancel: every order cancelled once, stock back to exactly 20
- PASS: mixed rush: units sold equal units in orders, stock never below 0
- PASS: mixed rush: nobody was turned away while enough stock was left
- PASS: no unexpected errors
