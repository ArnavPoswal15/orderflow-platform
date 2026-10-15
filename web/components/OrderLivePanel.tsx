"use client";

import { useEffect, useState } from "react";
import { ApiProblem, cancelOrder, fetchOrder } from "@/lib/api";
import type { EventEnvelope, OrderView, ProblemDetail } from "@/lib/types";
import { Money } from "./Money";
import { OrderTimeline } from "./OrderTimeline";
import { ProblemAlert } from "./ProblemAlert";
import { StatusBadge } from "./StatusBadge";

const CANCELLABLE = new Set(["PENDING", "RESERVED", "CONFIRMED"]);

type SseFrame = { data: string };

function drainSseFrames(buffer: string): { frames: SseFrame[]; rest: string } {
  const parts = buffer.split(/\r?\n\r?\n/);
  const rest = parts.pop() ?? "";
  const frames: SseFrame[] = [];
  for (const part of parts) {
    const dataLines: string[] = [];
    for (const line of part.split(/\r?\n/)) {
      if (!line || line.startsWith(":")) continue;
      if (line.startsWith("data:")) dataLines.push(line.slice(5).replace(/^ /, ""));
    }
    if (dataLines.length > 0) frames.push({ data: dataLines.join("\n") });
  }
  return { frames, rest };
}

function parseEventEnvelope(data: string): EventEnvelope | null {
  try {
    const value: unknown = JSON.parse(data);
    if (!value || typeof value !== "object") return null;
    const type = (value as { type?: unknown }).type;
    if (typeof type !== "string" || type.length === 0) return null;
    return value as EventEnvelope;
  } catch {
    return null;
  }
}

const IN_FLIGHT = new Set(["PENDING", "RESERVED"]);
const CATCH_UP_MS = 400;

function isNewerSnapshot(next: OrderView, current: OrderView): boolean {
  const nextAt = Date.parse(next.updatedAt);
  const currentAt = Date.parse(current.updatedAt);
  if (Number.isNaN(nextAt) || Number.isNaN(currentAt)) return true;
  if (nextAt !== currentAt) return nextAt > currentAt;
  return (next.history?.length ?? 0) >= (current.history?.length ?? 0);
}

function wait(ms: number, signal: AbortSignal): Promise<void> {
  if (signal.aborted) return Promise.resolve();
  return new Promise((resolve) => {
    const finish = () => {
      clearTimeout(timer);
      signal.removeEventListener("abort", finish);
      resolve();
    };
    const timer = setTimeout(finish, ms);
    signal.addEventListener("abort", finish);
  });
}

export function OrderLivePanel({ initial }: { initial: OrderView }) {
  const [order, setOrder] = useState(initial);
  const [live, setLive] = useState<"connecting" | "open" | "reconnecting">("connecting");
  const [cancelling, setCancelling] = useState(false);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let stopped = false;
    let status = initial.status;
    let appliedAt = Date.parse(initial.updatedAt);

    const refresh = () => {
      void fetchOrder(initial.id)
        .then((next) => {
          if (stopped) return;
          const nextAt = Date.parse(next.updatedAt);
          if (!Number.isNaN(nextAt) && !Number.isNaN(appliedAt) && nextAt < appliedAt) return;
          appliedAt = Number.isNaN(nextAt) ? appliedAt : nextAt;
          status = next.status;
          setOrder((current) => (isNewerSnapshot(next, current) ? next : current));
        })
        .catch(() => {
          // Keep the last good snapshot if a refresh misses.
        });
    };

    const connect = async () => {
      while (!stopped) {
        try {
          const res = await fetch(`/api/stream/orders/${initial.id}`, {
            headers: { Accept: "text/event-stream" },
            cache: "no-store",
            signal: controller.signal,
          });
          if (!res.ok || !res.body) {
            if (!stopped) setLive("reconnecting");
            await wait(2000, controller.signal);
            continue;
          }
          if (stopped) return;
          setLive("open");
          refresh();
          const reader = res.body.getReader();
          const decoder = new TextDecoder();
          let rest = "";
          while (!stopped) {
            const { done, value } = await reader.read();
            if (done) break;
            rest += decoder.decode(value, { stream: true });
            const drained = drainSseFrames(rest);
            rest = drained.rest;
            for (const frame of drained.frames) {
              if (parseEventEnvelope(frame.data)) refresh();
            }
          }
        } catch {
          if (stopped || controller.signal.aborted) return;
        }
        if (stopped) return;
        setLive("reconnecting");
        await wait(2000, controller.signal);
      }
    };

    refresh();
    void connect();
    // payment.succeeded can be published before this stream is subscribed, and the
    // refetch already in flight can still return RESERVED. Keep catching up until
    // the order leaves that window.
    const catchUp = window.setInterval(() => {
      if (IN_FLIGHT.has(status)) refresh();
    }, CATCH_UP_MS);

    return () => {
      stopped = true;
      window.clearInterval(catchUp);
      controller.abort();
    };
  }, [initial.id, initial.status, initial.updatedAt]);

  async function onCancel() {
    setCancelling(true);
    setProblem(null);
    try {
      const next = await cancelOrder(order.id, "customer_requested");
      setOrder(next);
    } catch (error) {
      if (error instanceof ApiProblem) {
        setProblem(error.problem);
      } else {
        setProblem({
          status: 0,
          title: "Cancel failed",
          detail: "Could not reach the shop. Try again.",
        });
      }
    } finally {
      setCancelling(false);
    }
  }

  const items = order.items ?? [];
  const liveLabel =
    live === "open" ? "Live" : live === "connecting" ? "Connecting" : "Reconnecting";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3" aria-live="polite">
          <StatusBadge status={order.status} />
          <span className="text-sm text-zinc-600">
            Total <Money cents={order.totalCents} />
          </span>
        </div>
        <p className="flex items-center gap-2 text-xs font-medium text-zinc-500">
          <span
            className={`inline-block h-2 w-2 rounded-full ${
              live === "open" ? "bg-emerald-500" : "bg-amber-400"
            }`}
            aria-hidden
          />
          {liveLabel}
        </p>
      </div>

      <OrderTimeline order={order} />

      <section className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-6">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500">Items</h2>
        <ul className="mt-3 divide-y divide-zinc-100">
          {items.map((item) => (
            <li key={item.sku} className="flex items-center justify-between gap-3 py-3 text-sm">
              <div>
                <p className="font-medium text-zinc-950">{item.sku}</p>
                <p className="text-zinc-500">
                  {item.qty} × <Money cents={item.unitPriceCents} />
                </p>
              </div>
              <Money cents={item.unitPriceCents * item.qty} />
            </li>
          ))}
        </ul>
        {order.paymentToken ? (
          <p className="mt-3 text-xs text-zinc-500">
            Payment token <span className="font-mono text-zinc-700">{order.paymentToken}</span>
          </p>
        ) : null}
      </section>

      {problem ? <ProblemAlert problem={problem} /> : null}

      {CANCELLABLE.has(order.status) ? (
        <button
          type="button"
          onClick={onCancel}
          disabled={cancelling}
          className="rounded-lg border border-rose-300 bg-white px-4 py-2 text-sm font-medium text-rose-800 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-700"
        >
          {cancelling ? "Cancelling…" : "Cancel order"}
        </button>
      ) : null}
    </div>
  );
}
