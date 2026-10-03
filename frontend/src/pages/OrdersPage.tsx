import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { api, formatDate, formatPrice } from "../api";
import type { Order, OrderStatus } from "../types";
import { useSession } from "../session";
import { payForOrder } from "../payment";
import Notice from "../components/Notice";

/** The words a customer sees for each order status. */
export const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "Awaiting payment",
  PLACED: "Paid",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

interface ArrivalState {
  paid?: string;
  unpaid?: string;
  problem?: string;
}

export default function OrdersPage() {
  const location = useLocation();
  const arrival = (location.state as ArrivalState | null) ?? {};
  const { user } = useSession();

  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState(arrival.problem ?? "");
  const [message, setMessage] = useState(
    arrival.paid
      ? `Payment received. Order ${arrival.paid} is confirmed.`
      : arrival.unpaid && !arrival.problem
        ? `Order ${arrival.unpaid} is waiting for payment. Pay within 15 minutes to keep it.`
        : "",
  );
  const [busyOrder, setBusyOrder] = useState("");

  function load() {
    api
      .myOrders()
      .then(setOrders)
      .catch((err: Error) => setError(err.message));
  }

  useEffect(load, []);

  async function pay(orderNumber: string) {
    setError("");
    setMessage("");
    setBusyOrder(orderNumber);

    try {
      const payment = await api.paymentInfo(orderNumber);
      const outcome = await payForOrder(orderNumber, payment, {
        name: user?.name ?? "",
        email: user?.email ?? "",
      });
      if (outcome === "paid") {
        setMessage(`Payment received. Order ${orderNumber} is confirmed.`);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyOrder("");
      load();
    }
  }

  async function cancel(orderNumber: string) {
    setError("");
    setMessage("");
    try {
      await api.cancelOrder(orderNumber);
      setMessage(`Order ${orderNumber} cancelled.`);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      load();
    }
  }

  return (
    <div className="orders">
      <h1>My orders</h1>

      {message && <Notice kind="success">{message}</Notice>}
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
                {STATUS_LABEL[order.status]}
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

            {order.status === "PENDING_PAYMENT" && (
              <div className="order-actions">
                <button
                  type="button"
                  className="button button-small"
                  disabled={busyOrder === order.orderNumber}
                  onClick={() => pay(order.orderNumber)}
                >
                  {busyOrder === order.orderNumber ? "Please wait…" : "Pay now"}
                </button>
                <button type="button" className="link-button" onClick={() => cancel(order.orderNumber)}>
                  Cancel order
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
