import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, formatPrice } from "../api";
import type { Cart } from "../types";
import { useSession } from "../session";
import Notice from "../components/Notice";
import { payForOrder } from "../payment";

export default function CartPage() {
  const navigate = useNavigate();
  const { user, setCartCount } = useSession();

  const [cart, setCart] = useState<Cart | null>(null);
  const [address, setAddress] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function show(next: Cart) {
    setCart(next);
    setCartCount(next.itemCount);
  }

  useEffect(() => {
    api
      .cart()
      .then(show)
      .catch((err: Error) => setError(err.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function changeQuantity(productId: number, quantity: number) {
    setError("");
    try {
      show(await api.setCartQuantity(productId, quantity));
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function placeOrder(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");

    let orderNumber = "";

    try {
      // Step 1: the order is created and the stock is held
      const result = await api.checkout(address);
      orderNumber = result.order.orderNumber;
      setCartCount(0);

      // Step 2: pay for it
      const outcome = await payForOrder(orderNumber, result.payment, {
        name: user?.name ?? "",
        email: user?.email ?? "",
      });

      navigate("/orders", { state: outcome === "paid" ? { paid: orderNumber } : { unpaid: orderNumber } });
    } catch (err) {
      if (orderNumber) {
        // The order exists but the payment did not go through: it can be paid from My orders
        navigate("/orders", { state: { unpaid: orderNumber, problem: (err as Error).message } });
        return;
      }
      // Most likely someone else bought the last units first: reload to show current stock
      setError((err as Error).message);
      api.cart().then(show).catch(() => undefined);
    } finally {
      setBusy(false);
    }
  }

  if (!cart) {
    return error ? <Notice kind="error">{error}</Notice> : <p className="empty">Loading cart…</p>;
  }

  if (cart.items.length === 0) {
    return (
      <div className="narrow">
        <h1>Your cart</h1>
        <p className="empty">
          Your cart is empty. <Link to="/">Browse the shop</Link> to add something.
        </p>
      </div>
    );
  }

  return (
    <div className="cart">
      <h1>Your cart</h1>

      {error && <Notice kind="error">{error}</Notice>}

      <div className="cart-layout">
        <table className="table">
          <thead>
            <tr>
              <th>Product</th>
              <th className="num">Price</th>
              <th>Quantity</th>
              <th className="num">Total</th>
              <th>
                <span className="visually-hidden">Remove</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {cart.items.map((line) => (
              <tr key={line.productId}>
                <td>
                  <Link to={`/products/${line.productId}`}>{line.name}</Link>
                  {line.quantity > line.stock && (
                    <span className="warn-text">Only {line.stock} left</span>
                  )}
                </td>
                <td className="num">{formatPrice(line.unitPrice)}</td>
                <td>
                  <div className="stepper">
                    <button
                      type="button"
                      aria-label={`One fewer ${line.name}`}
                      onClick={() => changeQuantity(line.productId, line.quantity - 1)}
                    >
                      −
                    </button>
                    <span>{line.quantity}</span>
                    <button
                      type="button"
                      aria-label={`One more ${line.name}`}
                      disabled={line.quantity >= line.stock}
                      onClick={() => changeQuantity(line.productId, line.quantity + 1)}
                    >
                      +
                    </button>
                  </div>
                </td>
                <td className="num">{formatPrice(line.lineTotal)}</td>
                <td>
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => changeQuantity(line.productId, 0)}
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <form className="checkout" onSubmit={placeOrder}>
          <h2>Place your order</h2>
          <p className="checkout-total">
            <span>Total</span>
            <strong>{formatPrice(cart.total)}</strong>
          </p>
          <label>
            Delivery address
            <textarea
              required
              rows={3}
              maxLength={500}
              value={address}
              onChange={(event) => setAddress(event.target.value)}
              placeholder="House, street, city, PIN code"
            />
          </label>
          <button type="submit" className="button button-wide" disabled={busy}>
            {busy ? "Please wait…" : "Place order and pay"}
          </button>
          <p className="fine-print">
            Your items are held for 15 minutes while you pay. Unpaid orders are cancelled automatically.
          </p>
        </form>
      </div>
    </div>
  );
}
