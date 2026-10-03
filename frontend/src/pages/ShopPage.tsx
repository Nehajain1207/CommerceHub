import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, formatPrice } from "../api";
import type { Category, Page, Product } from "../types";
import StockMeter from "../components/StockMeter";
import Notice from "../components/Notice";

const SORTS = [
  { value: "newest", label: "Newest first" },
  { value: "price_asc", label: "Price: low to high" },
  { value: "price_desc", label: "Price: high to low" },
  { value: "name", label: "Name: A to Z" },
];

export default function ShopPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [result, setResult] = useState<Page<Product> | null>(null);
  const [searchText, setSearchText] = useState("");
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [sort, setSort] = useState("newest");
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    api.categories().then(setCategories).catch(() => setCategories([]));
  }, []);

  // Wait until typing pauses before searching, so every keystroke is not a request
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(searchText.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    let cancelled = false;
    setError("");

    api
      .products({ q: query, categoryId, sort, page, size: 12 })
      .then((data) => {
        if (!cancelled) setResult(data);
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message);
      });

    return () => {
      cancelled = true;
    };
  }, [query, categoryId, sort, page]);

  function chooseCategory(id: number | null) {
    setCategoryId(id);
    setPage(0);
  }

  return (
    <div className="shop">
      <aside className="shop-side">
        <h2 className="side-title">Categories</h2>
        <ul className="category-list">
          <li>
            <button
              type="button"
              className={categoryId === null ? "is-current" : ""}
              onClick={() => chooseCategory(null)}
            >
              Everything
            </button>
          </li>
          {categories.map((category) => (
            <li key={category.id}>
              <button
                type="button"
                className={categoryId === category.id ? "is-current" : ""}
                onClick={() => chooseCategory(category.id)}
              >
                {category.name}
              </button>
            </li>
          ))}
        </ul>
      </aside>

      <section className="shop-main">
        <div className="shop-top">
          <h1>
            {categoryId === null
              ? "Everything in the shop"
              : categories.find((c) => c.id === categoryId)?.name}
          </h1>
          <div className="shop-controls">
            <label className="field-inline">
              <span className="visually-hidden">Search products</span>
              <input
                type="search"
                placeholder="Search products"
                value={searchText}
                onChange={(event) => setSearchText(event.target.value)}
              />
            </label>
            <label className="field-inline">
              <span className="visually-hidden">Sort by</span>
              <select
                value={sort}
                onChange={(event) => {
                  setSort(event.target.value);
                  setPage(0);
                }}
              >
                {SORTS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        {error && <Notice kind="error">{error}</Notice>}

        {result && result.content.length === 0 && !error && (
          <p className="empty">
            No products match{query ? ` "${query}"` : ""}. Try a different search or category.
          </p>
        )}

        <ul className="product-grid">
          {result?.content.map((product) => (
            <li key={product.id}>
              <Link to={`/products/${product.id}`} className="product-card">
                <span className="product-category">{product.categoryName}</span>
                <span className="product-name">{product.name}</span>
                <span className="price-tag">{formatPrice(product.price)}</span>
                <StockMeter stock={product.stock} />
              </Link>
            </li>
          ))}
        </ul>

        {result && result.totalPages > 1 && (
          <nav className="pager" aria-label="Pages">
            <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>
              Page {result.page + 1} of {result.totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= result.totalPages}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </nav>
        )}
      </section>
    </div>
  );
}
