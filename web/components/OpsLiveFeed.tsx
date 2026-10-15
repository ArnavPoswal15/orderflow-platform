"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { formatOccurredAt, occurredAtIso } from "@/lib/format";

const MAX_EVENTS = 100;

type LiveEvent = {
  eventId: string;
  type: string;
  orderId: string;
  occurredAt: string | number;
};

type SseFrame = { data: string };

const EVENT_STYLES: Record<string, { pill: string; bar: string }> = {
  "order.created": { pill: "bg-amber-100 text-amber-950 ring-amber-200", bar: "border-l-amber-400" },
  "inventory.reserved": { pill: "bg-sky-100 text-sky-950 ring-sky-200", bar: "border-l-sky-400" },
  "inventory.rejected": { pill: "bg-rose-100 text-rose-950 ring-rose-200", bar: "border-l-rose-400" },
  "inventory.released": { pill: "bg-orange-100 text-orange-950 ring-orange-200", bar: "border-l-orange-400" },
  "payment.succeeded": { pill: "bg-emerald-100 text-emerald-950 ring-emerald-200", bar: "border-l-emerald-500" },
  "payment.failed": { pill: "bg-rose-100 text-rose-950 ring-rose-200", bar: "border-l-rose-500" },
  "payment.refunded": { pill: "bg-violet-100 text-violet-950 ring-violet-200", bar: "border-l-violet-400" },
  "order.cancelled": { pill: "bg-rose-100 text-rose-950 ring-rose-200", bar: "border-l-rose-400" },
  "order.shipped": { pill: "bg-indigo-100 text-indigo-950 ring-indigo-200", bar: "border-l-indigo-400" },
  "order.delivered": { pill: "bg-zinc-200 text-zinc-900 ring-zinc-300", bar: "border-l-zinc-400" },
};

const DEFAULT_STYLE = { pill: "bg-zinc-100 text-zinc-800 ring-zinc-200", bar: "border-l-zinc-300" };

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

function parseLiveEvent(data: string): LiveEvent | null {
  try {
    const value: unknown = JSON.parse(data);
    if (!value || typeof value !== "object") return null;
    const record = value as Record<string, unknown>;
    if (typeof record.type !== "string" || record.type.length === 0) return null;
    const orderId = typeof record.orderId === "string" ? record.orderId : "";
    const occurredAt =
      typeof record.occurredAt === "string" || typeof record.occurredAt === "number" ? record.occurredAt : "";
    const eventId =
      typeof record.eventId === "string" && record.eventId.length > 0 ? record.eventId : data;
    return { eventId, type: record.type, orderId, occurredAt };
  } catch {
    return null;
  }
}

function remember(current: LiveEvent[], event: LiveEvent): LiveEvent[] {
  if (current.some((row) => row.eventId === event.eventId)) return current;
  const next = [event, ...current];
  return next.length > MAX_EVENTS ? next.slice(0, MAX_EVENTS) : next;
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

export function OpsLiveFeed() {
  const [events, setEvents] = useState<LiveEvent[]>([]);
  const [live, setLive] = useState<"connecting" | "open" | "reconnecting">("connecting");

  useEffect(() => {
    const controller = new AbortController();
    let stopped = false;

    // This stream does not replay. The list is only what arrives after subscribe.
    const connect = async () => {
      while (!stopped) {
        try {
          const res = await fetch("/api/stream/events", {
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
          const reader = res.body.getReader();
          const decoder = new TextDecoder();
          let rest = "";
          while (!stopped) {
            const { done, value } = await reader.read();
            if (done) break;
            rest += decoder.decode(value, { stream: true });
            const drained = drainSseFrames(rest);
            rest = drained.rest;
            const incoming: LiveEvent[] = [];
            for (const frame of drained.frames) {
              const event = parseLiveEvent(frame.data);
              if (event) incoming.push(event);
            }
            if (incoming.length > 0) {
              setEvents((current) => incoming.reduce(remember, current));
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

    void connect();
    return () => {
      stopped = true;
      controller.abort();
    };
  }, []);

  const liveLabel = live === "open" ? "Live" : live === "connecting" ? "Connecting" : "Reconnecting";

  return (
    <section className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500">Live events</h2>
        <p className="flex items-center gap-2 text-xs font-medium text-zinc-500">
          <span
            className={`inline-block h-2 w-2 rounded-full ${live === "open" ? "bg-emerald-500" : "bg-amber-400"}`}
            aria-hidden
          />
          <span aria-live="polite">{liveLabel}</span>
        </p>
      </div>
      <p className="mt-2 text-sm text-zinc-500">Newest first. Events appear as they are published.</p>
      {events.length === 0 ? (
        <p className="mt-4 text-sm text-zinc-600">Waiting for events.</p>
      ) : (
        <ul className="mt-4 max-h-96 divide-y divide-zinc-100 overflow-y-auto">
          {events.map((event) => {
            const style = EVENT_STYLES[event.type] ?? DEFAULT_STYLE;
            const iso = occurredAtIso(event.occurredAt);
            return (
              <li key={event.eventId} className={`border-l-4 ${style.bar} py-2 pl-3`}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <span
                      className={`inline-flex rounded-full px-2 py-0.5 font-mono text-xs font-medium ring-1 ring-inset ${style.pill}`}
                    >
                      {event.type}
                    </span>
                    {event.orderId ? (
                      <Link
                        href={`/orders/${event.orderId}`}
                        className="break-all font-mono text-xs text-zinc-950 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
                      >
                        {event.orderId}
                      </Link>
                    ) : null}
                  </div>
                  <time className="text-xs text-zinc-500" dateTime={iso}>
                    {formatOccurredAt(event.occurredAt)}
                  </time>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
