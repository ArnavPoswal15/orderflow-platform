import type { AdminStatsView } from "@/lib/types";
import { StatusBadge } from "./StatusBadge";

const STATUS_ORDER = ["PENDING", "RESERVED", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"];

const DLT_TOPICS = ["orders.events.DLT", "inventory.events.DLT", "payments.events.DLT"];

function statusRows(counts: Record<string, number>): { status: string; count: number }[] {
  const rows = STATUS_ORDER.map((status) => ({ status, count: counts[status] ?? 0 }));
  for (const [status, count] of Object.entries(counts)) {
    if (!STATUS_ORDER.includes(status)) rows.push({ status, count });
  }
  return rows;
}

function dltRows(counts: Record<string, number>): { topic: string; count: number }[] {
  const rows = DLT_TOPICS.map((topic) => ({ topic, count: counts[topic] ?? 0 }));
  for (const [topic, count] of Object.entries(counts)) {
    if (!DLT_TOPICS.includes(topic)) rows.push({ topic, count });
  }
  return rows;
}

export function OpsStatusPanel({ stats }: { stats: AdminStatsView }) {
  const statuses = statusRows(stats.ordersByStatus);
  const topics = dltRows(stats.dltCounts);
  const dltTotal = topics.reduce((sum, row) => sum + row.count, 0);

  return (
    <section className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-6">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500">Orders by status</h2>
      <ul className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        {statuses.map((row) => (
          <li key={row.status} className="rounded-lg bg-zinc-50 px-3 py-3">
            <StatusBadge status={row.status} />
            <p className="mt-2 text-2xl font-semibold tabular-nums text-zinc-950">{row.count}</p>
          </li>
        ))}
        <li className="rounded-lg bg-zinc-50 px-3 py-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-zinc-500">Outbox backlog</p>
          <p className="mt-2 text-2xl font-semibold tabular-nums text-zinc-950">{stats.outboxBacklog}</p>
        </li>
      </ul>

      <h3 className="mt-6 text-sm font-semibold uppercase tracking-wide text-zinc-500">Dead-letter topics</h3>
      <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-zinc-500">DLT total</p>
          <p className="mt-1 text-4xl font-semibold tabular-nums text-zinc-950">{dltTotal}</p>
        </div>
        <dl className="grid flex-1 gap-2 sm:max-w-md">
          {topics.map((row) => (
            <div key={row.topic} className="flex items-baseline justify-between gap-4">
              <dt className="font-mono text-xs text-zinc-600">{row.topic}</dt>
              <dd className="text-sm font-semibold tabular-nums text-zinc-950">{row.count}</dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  );
}
