"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { Money } from "@/components/Money";
import { ProblemAlert } from "@/components/ProblemAlert";
import { ApiProblem, postOrder } from "@/lib/api";
import { linesOverStock, useCatalogStock } from "@/lib/catalog-stock";
import { useCart } from "@/lib/cart";
import type { ProblemDetail } from "@/lib/types";

const ATTEMPT_STORAGE_KEY = "shop.checkoutAttempt";

type CheckoutBody = {
  items: { sku: string; qty: number }[];
  paymentToken: string;
};

type StoredAttempt = {
  fingerprint: string;
  key: string;
};

function readAttempt(): StoredAttempt | null {
  try {
    const raw = window.sessionStorage.getItem(ATTEMPT_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredAttempt;
    if (
      parsed &&
      typeof parsed.fingerprint === "string" &&
      typeof parsed.key === "string" &&
      parsed.key.length > 0
    ) {
      return parsed;
    }
  } catch {
    return null;
  }
  return null;
}

function keyForAttempt(body: CheckoutBody): string {
  const fingerprint = JSON.stringify(body);
  const existing = readAttempt();
  if (existing && existing.fingerprint === fingerprint) return existing.key;
  const key = crypto.randomUUID();
  window.sessionStorage.setItem(ATTEMPT_STORAGE_KEY, JSON.stringify({ fingerprint, key }));
  return key;
}

export function CheckoutForm() {
  const router = useRouter();
  const { lines, ready, subtotalCents, clear } = useCart();
  const bySku = useCatalogStock();
  const overStock = linesOverStock(lines, bySku);
  const [token, setToken] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [retryLeft, setRetryLeft] = useState(0);

  useEffect(() => {
    if (retryLeft <= 0) return;
    const id = window.setTimeout(() => setRetryLeft((current) => Math.max(0, current - 1)), 1000);
    return () => window.clearTimeout(id);
  }, [retryLeft]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || retryLeft > 0 || lines.length === 0 || overStock.length > 0) return;
    setSubmitting(true);
    setProblem(null);
    const body: CheckoutBody = {
      items: lines.map((line) => ({ sku: line.sku, qty: line.qty })),
      paymentToken: token.trim(),
    };
    const idempotencyKey = keyForAttempt(body);
    try {
      const order = await postOrder(body, idempotencyKey);
      window.sessionStorage.removeItem(ATTEMPT_STORAGE_KEY);
      clear();
      router.push(`/orders/${order.id}`);
    } catch (error) {
      if (error instanceof ApiProblem) {
        setProblem(error.problem);
        if (error.problem.status === 429) {
          setRetryLeft(error.problem.retryAfterSec ?? 1);
        }
      } else {
        setProblem({
          status: 0,
          title: "Checkout failed",
          detail: "Could not reach the shop. Try again.",
        });
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (!ready) return <p className="text-sm text-zinc-500">Loading cart…</p>;

  if (lines.length === 0) {
    return (
      <p className="text-sm text-zinc-600">
        Nothing to check out.{" "}
        <Link href="/" className="font-medium text-zinc-950 underline">
          Back to the shop
        </Link>
        .
      </p>
    );
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
      <section className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-6">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500">Payment</h2>
        <label htmlFor="paymentToken" className="mt-4 block text-sm font-medium text-zinc-900">
          Payment token
        </label>
        <input
          id="paymentToken"
          name="paymentToken"
          value={token}
          onChange={(event) => setToken(event.target.value)}
          required
          autoComplete="off"
          placeholder="ok"
          className="mt-2 w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-950 outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
        />
        <p className="mt-2 text-sm text-zinc-500">
          Token <code className="rounded bg-zinc-100 px-1 py-0.5 font-mono text-zinc-800">ok</code> succeeds. Token{" "}
          <code className="rounded bg-zinc-100 px-1 py-0.5 font-mono text-zinc-800">fail</code> declines. Any other
          token also succeeds while the payment failure-rate is 0.
        </p>
        {problem ? (
          <div className="mt-4">
            <ProblemAlert problem={problem} retrySecondsLeft={retryLeft} />
          </div>
        ) : null}
        {overStock.length > 0 ? (
          <p className="mt-4 text-sm text-amber-800">
            {overStock.map((line) => `${line.name} has only ${bySku.get(line.sku)} in stock`).join(". ")}. Lower the
            quantity in the cart before placing the order.
          </p>
        ) : null}
        <button
          type="submit"
          disabled={submitting || retryLeft > 0 || overStock.length > 0}
          className="mt-6 rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700 disabled:cursor-not-allowed disabled:bg-zinc-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
        >
          {submitting ? "Placing order…" : retryLeft > 0 ? `Retry in ${retryLeft}s` : "Place order"}
        </button>
      </section>
      <aside className="h-fit rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500">Review</h2>
        <ul className="mt-3 space-y-2 text-sm">
          {lines.map((line) => (
            <li key={line.sku} className="flex justify-between gap-3">
              <span>
                {line.name} <span className="text-zinc-500">× {line.qty}</span>
              </span>
              <Money cents={line.priceCents * line.qty} />
            </li>
          ))}
        </ul>
        <p className="mt-4 border-t border-zinc-100 pt-3 text-sm font-semibold">
          Subtotal <Money cents={subtotalCents} />
        </p>
      </aside>
    </form>
  );
}
