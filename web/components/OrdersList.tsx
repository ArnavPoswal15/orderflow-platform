"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiProblem, fetchOrderPage } from "@/lib/api";
import { formatTimestamp } from "@/lib/format";
import type { OrderPageView, OrderView, ProblemDetail } from "@/lib/types";
import { Money } from "./Money";
import { ProblemAlert } from "./ProblemAlert";
import { StatusBadge } from "./StatusBadge";

export function OrdersList({ initial }: { initial: OrderPageView }) {
  const [orders, setOrders] = useState<OrderView[]>(initial.content ?? []);
  const [page, setPage] = useState(initial.page);
  const [total, setTotal] = useState(initial.totalElements);
  const [loading, setLoading] = useState(false);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const size = initial.size || 20;

  async function loadMore() {
    setLoading(true);
    setProblem(null);
    try {
      const next = await fetchOrderPage(page + 1, size);
      setOrders((current) => {
        const seen = new Set(current.map((order) => order.id));
        return [...current, ...next.content.filter((order) => !seen.has(order.id))];
      });
      setPage(next.page);
      setTotal(next.totalElements);
    } catch (error) {
      if (error instanceof ApiProblem) setProblem(error.problem);
      else setProblem({ status: 0, title: "Could not load more orders", detail: "Try again." });
    } finally {
      setLoading(false);
    }
  }

  if (orders.length === 0) {
    return (
      <p className="text-sm text-zinc-600">
        No orders yet.{" "}
        <Link href="/" className="font-medium text-zinc-950 underline">
          Browse the shop
        </Link>
        .
      </p>
    );
  }

  return (
    <div className="space-y-4">
      <ul className="space-y-3 md:hidden">
        {orders.map((order) => (
          <li key={order.id} className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
            <Link href={`/orders/${order.id}`} className="font-mono text-xs text-zinc-950 underline">
              {order.id}
            </Link>
            <div className="mt-3 flex items-center justify-between gap-3">
              <StatusBadge status={order.status} />
              <Money cents={order.totalCents} />
            </div>
            <p className="mt-2 text-xs text-zinc-500">{formatTimestamp(order.createdAt)}</p>
          </li>
        ))}
      </ul>

      <div className="hidden overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-zinc-200 bg-zinc-50 text-xs uppercase tracking-wide text-zinc-500">
            <tr>
              <th className="px-4 py-3 font-medium">Order</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Total</th>
              <th className="px-4 py-3 font-medium">Created</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100">
            {orders.map((order) => (
              <tr key={order.id}>
                <td className="px-4 py-3">
                  <Link
                    href={`/orders/${order.id}`}
                    className="font-mono text-xs text-zinc-950 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
                  >
                    {order.id}
                  </Link>
                </td>
                <td className="px-4 py-3">
                  <StatusBadge status={order.status} />
                </td>
                <td className="px-4 py-3">
                  <Money cents={order.totalCents} />
                </td>
                <td className="px-4 py-3 text-zinc-600">{formatTimestamp(order.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {problem ? <ProblemAlert problem={problem} /> : null}

      {orders.length < total ? (
        <button
          type="button"
          onClick={loadMore}
          disabled={loading}
          className="rounded-lg border border-zinc-300 bg-white px-4 py-2 text-sm font-medium text-zinc-900 hover:bg-zinc-50 disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
        >
          {loading ? "Loading…" : "Load more"}
        </button>
      ) : (
        <p className="text-xs text-zinc-500">
          {total} {total === 1 ? "order" : "orders"}
        </p>
      )}
    </div>
  );
}
