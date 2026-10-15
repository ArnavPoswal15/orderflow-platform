"use client";

import Link from "next/link";
import { Money } from "@/components/Money";
import { PageHeader } from "@/components/PageHeader";
import { useCart } from "@/lib/cart";

export default function CartPage() {
  const { lines, ready, setQty, remove, subtotalCents } = useCart();

  return (
    <>
      <PageHeader title="Cart" description="Quantities stay in this browser until you check out." />
      {!ready ? <p className="text-sm text-zinc-500">Loading cart…</p> : null}
      {ready && lines.length === 0 ? (
        <p className="text-sm text-zinc-600">
          Your cart is empty.{" "}
          <Link href="/" className="font-medium text-zinc-950 underline">
            Back to the shop
          </Link>
          .
        </p>
      ) : null}
      {ready && lines.length > 0 ? (
        <div className="space-y-4">
          <ul className="divide-y divide-zinc-200 overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
            {lines.map((line) => (
              <li key={line.sku} className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="font-medium text-zinc-950">{line.name}</p>
                  <p className="font-mono text-xs text-zinc-500">{line.sku}</p>
                  <p className="mt-1 text-sm text-zinc-600">
                    <Money cents={line.priceCents} /> each
                  </p>
                </div>
                <div className="flex items-center justify-between gap-4 sm:justify-end">
                  <div className="flex items-center rounded-lg border border-zinc-300">
                    <button
                      type="button"
                      aria-label={`Decrease ${line.name} quantity`}
                      onClick={() => setQty(line.sku, line.qty - 1)}
                      className="px-3 py-2 text-zinc-800 hover:bg-zinc-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
                    >
                      −
                    </button>
                    <span className="min-w-8 text-center text-sm tabular-nums">{line.qty}</span>
                    <button
                      type="button"
                      aria-label={`Increase ${line.name} quantity`}
                      onClick={() => setQty(line.sku, line.qty + 1)}
                      className="px-3 py-2 text-zinc-800 hover:bg-zinc-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
                    >
                      +
                    </button>
                  </div>
                  <p className="min-w-16 text-right text-sm font-medium">
                    <Money cents={line.priceCents * line.qty} />
                  </p>
                  <button
                    type="button"
                    onClick={() => remove(line.sku)}
                    className="text-sm text-zinc-500 underline hover:text-zinc-900"
                  >
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
          <div className="flex flex-col items-stretch justify-between gap-3 sm:flex-row sm:items-center">
            <p className="text-base font-semibold text-zinc-950">
              Subtotal <Money cents={subtotalCents} />
            </p>
            <Link
              href="/checkout"
              className="inline-flex items-center justify-center rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
            >
              Checkout
            </Link>
          </div>
        </div>
      ) : null}
    </>
  );
}
