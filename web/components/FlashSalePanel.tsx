"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { ApiProblem, postFlashSale } from "@/lib/api";
import type { FlashSaleResult, ProblemDetail, ProductView } from "@/lib/types";
import { ProblemAlert } from "./ProblemAlert";
import { StatusBadge } from "./StatusBadge";

const DEFAULT_SKU = "LASTITEM";

function skuChoices(products: ProductView[], selected: string): { sku: string; label: string }[] {
  const choices = products.map((product) => ({
    sku: product.sku,
    label: `${product.name} (${product.sku})`,
  }));
  if (!choices.some((choice) => choice.sku === DEFAULT_SKU)) {
    choices.unshift({ sku: DEFAULT_SKU, label: DEFAULT_SKU });
  }
  if (selected && !choices.some((choice) => choice.sku === selected)) {
    choices.unshift({ sku: selected, label: selected });
  }
  return choices;
}

export function FlashSalePanel({ products }: { products: ProductView[] }) {
  const [sku, setSku] = useState(DEFAULT_SKU);
  const [countText, setCountText] = useState("50");
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<FlashSaleResult | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  const count = Number(countText);
  const countValid = countText.trim() !== "" && Number.isInteger(count) && count >= 1 && count <= 100;
  const choices = skuChoices(products, sku);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (running || !countValid || sku.length === 0) return;
    setRunning(true);
    setProblem(null);
    setResult(null);
    try {
      const next = await postFlashSale({ sku, concurrentOrders: count });
      setResult(next);
    } catch (error) {
      if (error instanceof ApiProblem) setProblem(error.problem);
      else {
        setProblem({
          status: 0,
          title: "Flash sale failed",
          detail: "Could not reach the shop. Try again.",
        });
      }
    } finally {
      setRunning(false);
    }
  }

  const exact =
    result != null && result.confirmed === 1 && result.cancelled === result.concurrentOrders - 1;

  return (
    <section className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-6">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500">Flash sale</h2>
      <p className="mt-2 text-sm text-zinc-600">Set the failure rate slider to 0% first.</p>
      <form onSubmit={onSubmit} className="mt-4 space-y-4">
        <div>
          <label htmlFor="flash-sale-sku" className="block text-sm font-medium text-zinc-900">
            SKU
          </label>
          <select
            id="flash-sale-sku"
            value={sku}
            onChange={(event) => setSku(event.target.value)}
            disabled={running}
            className="mt-2 w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-950 outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900 disabled:opacity-60"
          >
            {choices.map((choice) => (
              <option key={choice.sku} value={choice.sku}>
                {choice.label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="flash-sale-count" className="block text-sm font-medium text-zinc-900">
            Concurrent orders
          </label>
          <input
            id="flash-sale-count"
            type="number"
            inputMode="numeric"
            min={1}
            max={100}
            step={1}
            value={countText}
            onChange={(event) => setCountText(event.target.value)}
            disabled={running}
            className="mt-2 w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-950 outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900 disabled:opacity-60"
          />
          <p className="mt-1 text-xs text-zinc-500">1 to 100.</p>
        </div>
        <button
          type="submit"
          disabled={running || !countValid || sku.length === 0}
          aria-busy={running}
          className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700 disabled:cursor-not-allowed disabled:bg-zinc-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
        >
          {running ? "Running flash sale…" : "Flash sale"}
        </button>
      </form>

      {running ? (
        <p className="mt-4 text-sm text-zinc-600" role="status">
          Waiting for every order to finish.
        </p>
      ) : null}
      {problem ? (
        <div className="mt-4">
          <ProblemAlert problem={problem} />
        </div>
      ) : null}

      {result ? (
        <div className="mt-6">
          <div aria-live="polite">
            <p className="text-sm text-zinc-600">
              {result.sku} · {result.concurrentOrders} orders
            </p>
            <div className="mt-3 grid grid-cols-2 gap-4">
              <div>
                <p className="text-xs font-semibold tracking-wide text-emerald-800">CONFIRMED</p>
                <p className="mt-1 text-5xl font-semibold tabular-nums text-emerald-700">{result.confirmed}</p>
              </div>
              <div>
                <p className="text-xs font-semibold tracking-wide text-rose-800">CANCELLED</p>
                <p className="mt-1 text-5xl font-semibold tabular-nums text-rose-700">{result.cancelled}</p>
              </div>
            </div>
            {exact ? (
              <p className="mt-4 text-sm font-medium text-zinc-900">
                Exactly 1 confirmed and {result.cancelled} cancelled.
              </p>
            ) : null}
          </div>
          <h3 className="mt-6 text-sm font-semibold text-zinc-800">Orders</h3>
          <ul className="mt-2 max-h-80 divide-y divide-zinc-100 overflow-y-auto">
            {result.orders.map((order, index) => (
              <li key={`${order.orderId || "order"}-${index}`} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  {order.orderId ? (
                    <Link
                      href={`/orders/${order.orderId}`}
                      className="break-all font-mono text-xs text-zinc-950 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
                    >
                      {order.orderId}
                    </Link>
                  ) : (
                    <span className="text-xs text-zinc-500">Order</span>
                  )}
                  {order.cancelReason ? (
                    <p className="mt-1 text-sm text-zinc-600">Reason: {order.cancelReason}</p>
                  ) : null}
                </div>
                {order.status ? <StatusBadge status={order.status} /> : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}
