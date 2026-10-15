export function formatCents(cents: number): string {
  const sign = cents < 0 ? "-" : "";
  const abs = Math.trunc(Math.abs(cents));
  const dollars = Math.floor(abs / 100);
  const rem = abs % 100;
  return `${sign}$${dollars.toLocaleString("en-US")}.${rem.toString().padStart(2, "0")}`;
}

export function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "medium",
  }).format(date);
}

/** SSE `occurredAt` is an ISO string or epoch seconds (Jackson Instant). */
export function formatOccurredAt(value: string | number): string {
  const iso = occurredAtIso(value);
  if (!iso) return "—";
  return formatTimestamp(iso);
}

export function occurredAtIso(value: string | number): string | undefined {
  if (typeof value === "number" && Number.isFinite(value)) {
    const ms = Math.abs(value) < 1e12 ? value * 1000 : value;
    const date = new Date(ms);
    return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
  }
  if (typeof value === "string" && value.length > 0) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
  }
  return undefined;
}

export function historyReason(data: Record<string, unknown> | null | undefined): string | null {
  if (!data) return null;
  const reason = data.reason;
  return typeof reason === "string" && reason.length > 0 ? reason : null;
}
