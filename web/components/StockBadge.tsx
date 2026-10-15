/** 1 through 5 units is low stock. The products API sends `available` only. */
const LOW_STOCK_AT_OR_BELOW = 5;

export function StockBadge({ available }: { available: number }) {
  if (available <= 0) {
    return (
      <span className="inline-flex items-center rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-medium text-rose-800 ring-1 ring-inset ring-rose-200">
        Out of stock
      </span>
    );
  }
  if (available <= LOW_STOCK_AT_OR_BELOW) {
    return (
      <span className="inline-flex items-center rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-950 ring-1 ring-inset ring-amber-200">
        Low stock · {available} left
      </span>
    );
  }
  return (
    <span className="inline-flex items-center rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-800 ring-1 ring-inset ring-emerald-200">
      {available} in stock
    </span>
  );
}
