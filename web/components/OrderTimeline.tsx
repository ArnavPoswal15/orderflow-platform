import { formatTimestamp, historyReason } from "@/lib/format";
import type { OrderView } from "@/lib/types";

const PROGRESS = ["PENDING", "RESERVED", "CONFIRMED", "SHIPPED", "DELIVERED"] as const;

const HISTORY_STATUS: Record<string, string> = {
  "order.created": "PENDING",
  "inventory.reserved": "RESERVED",
  "payment.succeeded": "CONFIRMED",
  "order.shipped": "SHIPPED",
  "order.delivered": "DELIVERED",
};

type StepState = "done" | "current" | "upcoming" | "cancelled";

function stepsFor(order: OrderView): { step: string; state: StepState }[] {
  if (order.status === "CANCELLED") {
    const reached = new Set<string>();
    for (const entry of order.history ?? []) {
      const mapped = HISTORY_STATUS[entry.type];
      if (mapped) reached.add(mapped);
    }
    const prior = PROGRESS.filter((step) => reached.has(step));
    return [
      ...prior.map((step) => ({ step, state: "done" as const })),
      { step: "CANCELLED", state: "cancelled" as const },
    ];
  }

  const steps: string[] = ["PENDING", "RESERVED", "CONFIRMED"];
  if (order.status === "SHIPPED" || order.status === "DELIVERED") steps.push("SHIPPED");
  if (order.status === "DELIVERED") steps.push("DELIVERED");
  const currentIndex = Math.max(0, steps.indexOf(order.status));
  return steps.map((step, index) => ({
    step,
    state: index < currentIndex ? "done" : index === currentIndex ? "current" : "upcoming",
  }));
}

const CIRCLE: Record<StepState, string> = {
  done: "bg-emerald-600 text-white",
  current: "bg-zinc-900 text-white ring-4 ring-zinc-200",
  upcoming: "bg-zinc-100 text-zinc-500",
  cancelled: "bg-rose-600 text-white ring-4 ring-rose-100",
};

export function OrderTimeline({ order }: { order: OrderView }) {
  const steps = stepsFor(order);
  const history = order.history ?? [];

  return (
    <section className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-6">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500">Timeline</h2>
      <ol className="mt-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        {steps.map((item, index) => (
          <li key={item.step} className="flex items-center gap-2 sm:gap-3">
            <span
              className={`inline-flex h-8 min-w-8 items-center justify-center rounded-full px-2 text-xs font-semibold ${CIRCLE[item.state]}`}
              aria-current={item.state === "current" || item.state === "cancelled" ? "step" : undefined}
            >
              {index + 1}
            </span>
            <span
              className={`text-sm font-medium ${
                item.state === "upcoming" ? "text-zinc-600" : "text-zinc-950"
              }`}
            >
              {item.step}
            </span>
            {index < steps.length - 1 ? (
              <span aria-hidden className="hidden text-zinc-300 sm:inline">
                →
              </span>
            ) : null}
          </li>
        ))}
      </ol>

      {order.status === "CANCELLED" && order.cancelReason ? (
        <p className="mt-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-900">
          Cancel reason: <span className="font-medium">{order.cancelReason}</span>
        </p>
      ) : null}

      <h3 className="mt-6 text-sm font-semibold text-zinc-800">History</h3>
      {history.length === 0 ? (
        <p className="mt-2 text-sm text-zinc-500">No events recorded yet.</p>
      ) : (
        <ul className="mt-2 divide-y divide-zinc-100">
          {history.map((entry, index) => {
            const reason = historyReason(entry.data);
            return (
              <li key={`${entry.type}-${entry.at}-${index}`} className="py-3">
                <div className="flex flex-col gap-1 sm:flex-row sm:items-baseline sm:justify-between">
                  <span className="font-mono text-sm text-zinc-900">{entry.type}</span>
                  <time className="text-xs text-zinc-500" dateTime={entry.at}>
                    {formatTimestamp(entry.at)}
                  </time>
                </div>
                {reason ? <p className="mt-1 text-sm text-zinc-600">Reason: {reason}</p> : null}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
