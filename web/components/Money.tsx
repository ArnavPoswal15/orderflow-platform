import { formatCents } from "@/lib/format";

export function Money({ cents }: { cents: number }) {
  return <span className="tabular-nums">{formatCents(cents)}</span>;
}
