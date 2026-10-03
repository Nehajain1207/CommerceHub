// Shows how much is left as a short bar plus words. Stock is the thing this shop
// is careful about, so it is visible everywhere a product appears.

const FULL_AT = 40;

export function stockLabel(stock: number): string {
  if (stock === 0) return "Sold out";
  if (stock <= 5) return `Only ${stock} left`;
  return `${stock} in stock`;
}

export function stockLevel(stock: number): "out" | "low" | "ok" {
  if (stock === 0) return "out";
  if (stock <= 5) return "low";
  return "ok";
}

export default function StockMeter({ stock }: { stock: number }) {
  const level = stockLevel(stock);
  const percent = Math.min(100, Math.round((stock / FULL_AT) * 100));

  return (
    <div className={`stock stock-${level}`}>
      <div className="stock-bar" aria-hidden="true">
        <span style={{ width: `${stock === 0 ? 0 : Math.max(percent, 6)}%` }} />
      </div>
      <span className="stock-text">{stockLabel(stock)}</span>
    </div>
  );
}
