-- Version 2: indexes for the queries the shop runs most often.
-- A separate file on purpose: this is how a schema change is shipped after version 1 is live.

-- "My orders", newest first
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);

-- The timer that cancels unpaid orders looks them up by status and age
CREATE INDEX idx_orders_status_created ON orders (status, created_at);

-- The outbox publisher reads only rows that have not been sent yet.
-- A partial index keeps it small however many events have already gone out.
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

-- Shop browsing by category
CREATE INDEX idx_products_category_active ON products (category_id, active);
