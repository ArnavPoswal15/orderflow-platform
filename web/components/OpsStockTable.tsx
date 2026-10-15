import type { ProductView } from "@/lib/types";
import { Money } from "./Money";
import { StockBadge } from "./StockBadge";

export function OpsStockTable({ products }: { products: ProductView[] }) {
  return (
    <section aria-labelledby="ops-stock-heading">
      <h2 id="ops-stock-heading" className="text-sm font-semibold uppercase tracking-wide text-zinc-500">
        Stock
      </h2>
      {products.length === 0 ? (
        <p className="mt-3 text-sm text-zinc-600">No products are in the catalog.</p>
      ) : (
        <>
          <ul className="mt-3 space-y-3 md:hidden">
            {products.map((product) => (
              <li key={product.sku} className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-medium text-zinc-950">{product.name}</p>
                    <p className="mt-1 font-mono text-xs text-zinc-500">{product.sku}</p>
                  </div>
                  <StockBadge available={product.available} />
                </div>
                <p className="mt-3 text-sm font-semibold text-zinc-950">
                  <Money cents={product.priceCents} />
                </p>
              </li>
            ))}
          </ul>
          <div className="mt-3 hidden overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm md:block">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200 bg-zinc-50 text-xs uppercase tracking-wide text-zinc-500">
                <tr>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Product
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    SKU
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Price
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Available
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100">
                {products.map((product) => (
                  <tr key={product.sku}>
                    <td className="px-4 py-3 font-medium text-zinc-950">{product.name}</td>
                    <td className="px-4 py-3 font-mono text-xs text-zinc-600">{product.sku}</td>
                    <td className="px-4 py-3">
                      <Money cents={product.priceCents} />
                    </td>
                    <td className="px-4 py-3">
                      <StockBadge available={product.available} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
