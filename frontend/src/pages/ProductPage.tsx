import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, formatPrice } from "../api";
import type { Product } from "../types";
import { useSession } from "../session";
import StockMeter from "../components/StockMeter";
import Notice from "../components/Notice";

export default function ProductPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user, setCartCount } = useSession();

  const [product, setProduct] = useState<Product | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [error, setError] = useState("");
  const [added, setAdded] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setError("");
    api
      .product(Number(id))
      .then(setProduct)
      .catch((err: Error) => setError(err.message));
  }, [id]);

  async function addToCart() {
    if (!product) return;

    if (!user) {
      navigate("/login", { state: { from: `/products/${product.id}` } });
      return;
    }

    setBusy(true);
    setError("");
    setAdded("");

    try {
      const cart = await api.addToCart(product.id, quantity);
      setCartCount(cart.itemCount);
      setAdded(`Added ${quantity} to your cart.`);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (error && !product) {
    return (
      <div className="narrow">
        <Notice kind="error">{error}</Notice>
        <Link to="/">Back to the shop</Link>
      </div>
    );
  }

  if (!product) {
    return <p className="empty">Loading product…</p>;
  }

  const soldOut = product.stock === 0;

  return (
    <article className="product-detail">
      <Link to="/" className="back-link">
        Back to the shop
      </Link>

      <p className="product-category">{product.categoryName}</p>
      <h1>{product.name}</h1>
      <p className="product-description">{product.description}</p>

      <div className="buy-box">
        <span className="price-tag price-tag-large">{formatPrice(product.price)}</span>
        <StockMeter stock={product.stock} />

        {!soldOut && (
          <div className="buy-row">
            <label>
              Quantity
              <input
                type="number"
                min={1}
                max={product.stock}
                value={quantity}
                onChange={(event) =>
                  setQuantity(Math.max(1, Math.min(product.stock, Number(event.target.value) || 1)))
                }
              />
            </label>
            <button type="button" className="button" onClick={addToCart} disabled={busy}>
              {busy ? "Adding…" : "Add to cart"}
            </button>
          </div>
        )}

        {error && <Notice kind="error">{error}</Notice>}
        {added && (
          <p className="notice notice-success" role="status">
            {added} <Link to="/cart">Go to cart</Link>
          </p>
        )}
      </div>

      <p className="sku">SKU {product.sku}</p>
    </article>
  );
}
