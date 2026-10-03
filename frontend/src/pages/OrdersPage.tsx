import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { api, formatDate, formatPrice } from "../api";
import type { Order } from "../types";
import Notice from "../components/Notice";

export default function OrdersPage() {
  const location = useLocation();
  const placed = (location.state as { placed?: string } | null)?.placed;

  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState("");

  function load() {
    api
      .myOrders()
      .then(setOrders)
      .catch((err: Error) => setError(err.message));
  }

  useEffect(load, []);

  async function cancel(orderNumber: string) {
    setError("");
    try {
      await api.cancelOrder(orderNumber);
      load();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div className="orders">
      <h1>My orders</h1>

      {placed && <Notice kind="success">{`Order ${placed} placed.`}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}

      {orders && orders.length === 0 && (
        <p className="empty">
          You have not ordered anything yet. <Link to="/">Browse the shop</Link>.
        </p>
      )}

      <ul className="order-list">
        {orders?.map((order) => (
          <li key={order.orderNumber} className="order">
            <div className="order-head">
              <div>
                <h2>{order.orderNumber}</h2>
                <p className="muted">Placed {formatDate(order.createdAt)}</p>
              </div>
              <span className={`status status-${order.status.toLowerCase()}`}>
                {order.status.charAt(0) + order.status.slice(1).toLowerCase()}
              </span>
            </div>

            <ul className="order-lines">
              {order.items.map((line) => (
                <li key={line.productId}>
                  <span>
                    {line.quantity} × {line.name}
                  </span>
                  <span>{formatPrice(line.lineTotal)}</span>
                </li>
              ))}
            </ul>

            <div className="order-foot">
              <span className="muted">Deliver to: {order.shippingAddress}</span>
              <strong>{formatPrice(order.total)}</strong>
            </div>

            {order.status === "PLACED" && (
              <button type="button" className="link-button" onClick={() => cancel(order.orderNumber)}>
                Cancel order
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
