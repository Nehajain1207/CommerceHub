# Flash sale test

- Customers checking out at the same moment: 500
- Units on sale: 50
- Orders placed: 50 (expected 50)
- Told "sold out": 450
- Unexpected responses: 0
- Stock left: 0 (expected 0)
- Checkout latency: median 8974 ms, p95 16187 ms, p99 16303 ms, max 16329 ms
- Result: PASS - no overselling, no lost orders
