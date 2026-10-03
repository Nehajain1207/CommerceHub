import { useEffect, useState } from "react";
import { api, formatDate, formatPrice } from "../api";
import type { Order, OrderStatus, Page } from "../types";
import Notice from "../components/Notice";
import { STATUS_LABEL } from "./OrdersPage";

/** The next step an admin can take for an order in each status. */
const NEXT_STEP: Partial<Record<OrderStatus, { status: OrderStatus; label: string }>> = {
  PLACED: { status: "SHIPPED", label: "Mark as shipped" },
  SHIPPED: { status: "DELIVERED", label: "Mark as delivered" },
};

export default function AdminOrdersPage() {
  const [result, setResult] = useState<Page<Order> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");

  function load() {
    api
      .adminOrders(page)
      .then(setResult)
      .catch((err: Error) => setError(err.message));
  }

  useEffect(load, [page]);

  async function move(orderNumber: string, status: OrderStatus) {
    setError("");
    try {
      await api.setOrderStatus(orderNumber, status);
      load();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div>
      <h1>Manage orders</h1>

      {error && <Notice kind="error">{error}</Notice>}

      {result && result.content.length === 0 && <p className="empty">No orders yet.</p>}

      {result && result.content.length > 0 && (
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Customer</th>
                <th>Items</th>
                <th className="num">Total</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {result.content.map((order) => {
                const next = NEXT_STEP[order.status];
                return (
                  <tr key={order.orderNumber}>
                    <td>
                      {order.orderNumber}
                      <span className="sub">{formatDate(order.createdAt)}</span>
                    </td>
                    <td>
                      {order.customerName}
                      <span className="sub">{order.customerEmail}</span>
                    </td>
                    <td>
                      {order.items.map((line) => (
                        <span className="sub-line" key={line.productId}>
                          {line.quantity} × {line.name}
                        </span>
                      ))}
                    </td>
                    <td className="num">{formatPrice(order.total)}</td>
                    <td>
                      <span className={`status status-${order.status.toLowerCase()}`}>
                        {STATUS_LABEL[order.status]}
                      </span>
                    </td>
                    <td>
                      {next && (
                        <button
                          type="button"
                          className="button button-small"
                          onClick={() => move(order.orderNumber, next.status)}
                        >
                          {next.label}
                        </button>
                      )}
                      {order.status === "PENDING_PAYMENT" && (
                        <button
                          type="button"
                          className="link-button"
                          onClick={() => move(order.orderNumber, "CANCELLED")}
                        >
                          Cancel
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {result && result.totalPages > 1 && (
        <nav className="pager" aria-label="Pages">
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <span>
            Page {result.page + 1} of {result.totalPages}
          </span>
          <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>
            Next
          </button>
        </nav>
      )}
    </div>
  );
}
