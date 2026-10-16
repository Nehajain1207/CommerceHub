# Flash sale test

- Customers checking out at the same moment: 500
- Units on sale: 50
- Orders placed: 50 (expected 50)
- Told "sold out": 450
- Unexpected responses: 0
- Stock left: 0 (expected 0)
- Checkout latency: median 12996 ms, p95 15277 ms, p99 15408 ms, max 15465 ms
- Result: PASS - no overselling, no lost orders
