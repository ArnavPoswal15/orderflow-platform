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

export function historyReason(data: Record<string, unknown> | null | undefined): string | null {
  if (!data) return null;
  const reason = data.reason;
  return typeof reason === "string" && reason.length > 0 ? reason : null;
}
