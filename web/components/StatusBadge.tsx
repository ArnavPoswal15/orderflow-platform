const TONES: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-900 ring-amber-200",
  RESERVED: "bg-sky-100 text-sky-900 ring-sky-200",
  CONFIRMED: "bg-emerald-100 text-emerald-900 ring-emerald-200",
  SHIPPED: "bg-indigo-100 text-indigo-900 ring-indigo-200",
  DELIVERED: "bg-zinc-200 text-zinc-900 ring-zinc-300",
  CANCELLED: "bg-rose-100 text-rose-900 ring-rose-200",
};

export function StatusBadge({ status }: { status: string }) {
  const tone = TONES[status] ?? "bg-zinc-100 text-zinc-800 ring-zinc-200";
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold tracking-wide ring-1 ring-inset ${tone}`}
    >
      {status}
    </span>
  );
}
