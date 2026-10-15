"use client";

import { useEffect, useState } from "react";
import { ApiProblem, fetchProducts } from "@/lib/api";
import { useCart } from "@/lib/cart";
import type { ProblemDetail, ProductView } from "@/lib/types";
import { Money } from "./Money";
import { ProblemAlert } from "./ProblemAlert";
import { StockBadge } from "./StockBadge";

const POLL_MS = 5000;

export function ProductGrid({ initialProducts }: { initialProducts: ProductView[] }) {
  const { addItem, lines } = useCart();
  const [products, setProducts] = useState(initialProducts);
  const [refreshProblem, setRefreshProblem] = useState<ProblemDetail | null>(null);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      try {
        const next = await fetchProducts();
        if (!cancelled) {
          setProducts(next);
          setRefreshProblem(null);
          setRefreshError(null);
        }
      } catch (error) {
        if (!cancelled) {
          if (error instanceof ApiProblem) {
            setRefreshProblem(error.problem);
            setRefreshError(null);
          } else {
            setRefreshProblem(null);
            setRefreshError("Stock refresh failed. Showing the last list.");
          }
        }
      }
    };
    const id = window.setInterval(tick, POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, []);

  if (products.length === 0) {
    return <p className="text-sm text-zinc-600">No products are in the catalog.</p>;
  }

  return (
    <div>
      <p className="mb-4 text-sm text-zinc-500">Stock refreshes every 5 seconds.</p>
      {refreshProblem ? (
        <div className="mb-4">
          <ProblemAlert problem={refreshProblem} />
        </div>
      ) : null}
      {refreshError ? (
        <p className="mb-4 text-sm text-amber-800" role="status">
          {refreshError}
        </p>
      ) : null}
      <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {products.map((product) => {
          const inCart = lines.find((line) => line.sku === product.sku)?.qty ?? 0;
          const soldOut = product.available === 0;
          const atStock = !soldOut && inCart >= product.available;
          return (
            <li
              key={product.sku}
              className="flex flex-col rounded-xl border border-zinc-200 bg-white p-4 shadow-sm"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="text-base font-semibold text-zinc-950">{product.name}</h2>
                  <p className="mt-1 font-mono text-xs text-zinc-500">{product.sku}</p>
                </div>
                <StockBadge available={product.available} />
              </div>
              <p className="mt-4 text-lg font-semibold text-zinc-950">
                <Money cents={product.priceCents} />
              </p>
              <div className="mt-4 flex items-center justify-between gap-3">
                <button
                  type="button"
                  disabled={soldOut || atStock}
                  onClick={() => addItem(product)}
                  className="rounded-lg bg-zinc-900 px-3 py-2 text-sm font-medium text-white hover:bg-zinc-700 disabled:cursor-not-allowed disabled:bg-zinc-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
                >
                  {atStock ? "All in cart" : "Add to cart"}
                </button>
                {inCart > 0 ? (
                  <span className="text-xs text-zinc-500">In cart: {inCart}</span>
                ) : null}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
