import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { api, formatPrice } from "../api";
import type { Category, Page, Product, ProductForm } from "../types";
import StockMeter from "../components/StockMeter";
import Notice from "../components/Notice";

const EMPTY_FORM: ProductForm = {
  sku: "",
  name: "",
  description: "",
  price: 0,
  stock: 0,
  categoryId: 0,
  active: true,
};

export default function AdminProductsPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [result, setResult] = useState<Page<Product> | null>(null);
  const [lowStock, setLowStock] = useState<Product[]>([]);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);

  // null = form closed, 0 = adding a new product, otherwise the id being edited
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<ProductForm>(EMPTY_FORM);

  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  function load() {
    api
      .adminProducts(search.trim(), page)
      .then(setResult)
      .catch((err: Error) => setError(err.message));
    api
      .lowStock(5)
      .then(setLowStock)
      .catch(() => setLowStock([]));
  }

  useEffect(() => {
    api.categories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    const timer = setTimeout(load, 250);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, page]);

  function startAdding() {
    setForm({ ...EMPTY_FORM, categoryId: categories[0]?.id ?? 0 });
    setEditingId(0);
    setError("");
    setMessage("");
  }

  function startEditing(product: Product) {
    setForm({
      sku: product.sku,
      name: product.name,
      description: product.description ?? "",
      price: product.price,
      stock: product.stock,
      categoryId: product.categoryId,
      active: product.active,
    });
    setEditingId(product.id);
    setError("");
    setMessage("");
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    setError("");

    try {
      if (editingId === 0) {
        await api.createProduct(form);
        setMessage(`${form.name} added.`);
      } else if (editingId !== null) {
        await api.updateProduct(editingId, form);
        setMessage(`${form.name} saved.`);
      }
      setEditingId(null);
      load();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function adjust(product: Product) {
    const answer = window.prompt(
      `Change stock for ${product.name} (now ${product.stock}).\nEnter +20 to add 20, or -3 to remove 3.`,
    );
    if (answer === null) return;

    const delta = Number(answer.trim());
    if (!Number.isInteger(delta) || delta === 0) {
      setError("Enter a whole number such as +20 or -3.");
      return;
    }

    setError("");
    try {
      const updated = await api.adjustStock(product.id, delta);
      setMessage(`${updated.name} stock is now ${updated.stock}.`);
      load();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div>
      <div className="title-row">
        <h1>Manage products</h1>
        <button type="button" className="button" onClick={startAdding}>
          Add product
        </button>
      </div>

      {error && <Notice kind="error">{error}</Notice>}
      {message && <Notice kind="success">{message}</Notice>}

      {lowStock.length > 0 && (
        <section className="low-stock" aria-label="Low stock">
          <h2>Running low</h2>
          <ul>
            {lowStock.map((product) => (
              <li key={product.id}>
                <span>{product.name}</span>
                <StockMeter stock={product.stock} />
                <button type="button" className="link-button" onClick={() => adjust(product)}>
                  Change stock
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {editingId !== null && (
        <form className="form product-form" onSubmit={save}>
          <h2>{editingId === 0 ? "Add product" : "Edit product"}</h2>
          <div className="form-grid">
            <label>
              Name
              <input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </label>
            <label>
              SKU
              <input required value={form.sku} onChange={(e) => setForm({ ...form, sku: e.target.value })} />
            </label>
            <label>
              Price (₹)
              <input
                required
                type="number"
                min={0.01}
                step="0.01"
                value={form.price || ""}
                onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
              />
            </label>
            <label>
              Category
              <select
                required
                value={form.categoryId}
                onChange={(e) => setForm({ ...form, categoryId: Number(e.target.value) })}
              >
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </label>
            {editingId === 0 && (
              <label>
                Starting stock
                <input
                  required
                  type="number"
                  min={0}
                  step={1}
                  value={form.stock}
                  onChange={(e) => setForm({ ...form, stock: Number(e.target.value) })}
                />
              </label>
            )}
            <label className="check">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm({ ...form, active: e.target.checked })}
              />
              Show in shop
            </label>
          </div>
          <label>
            Description
            <textarea
              rows={2}
              maxLength={1000}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </label>
          <div className="form-actions">
            <button type="submit" className="button">
              {editingId === 0 ? "Add product" : "Save changes"}
            </button>
            <button type="button" className="link-button" onClick={() => setEditingId(null)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      <label className="field-inline admin-search">
        <span className="visually-hidden">Search products</span>
        <input
          type="search"
          placeholder="Search products"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
        />
      </label>

      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Category</th>
              <th className="num">Price</th>
              <th>Stock</th>
              <th>In shop</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {result?.content.map((product) => (
              <tr key={product.id} className={product.active ? "" : "is-hidden-row"}>
                <td>
                  {product.name}
                  <span className="sub">{product.sku}</span>
                </td>
                <td>{product.categoryName}</td>
                <td className="num">{formatPrice(product.price)}</td>
                <td>
                  <StockMeter stock={product.stock} />
                </td>
                <td>{product.active ? "Yes" : "Hidden"}</td>
                <td className="actions">
                  <button type="button" className="link-button" onClick={() => startEditing(product)}>
                    Edit
                  </button>
                  <button type="button" className="link-button" onClick={() => adjust(product)}>
                    Change stock
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

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
