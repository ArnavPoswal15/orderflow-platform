import type { ProblemDetail } from "@/lib/types";

export function ProblemAlert({
  problem,
  retrySecondsLeft,
}: {
  problem: ProblemDetail;
  retrySecondsLeft?: number;
}) {
  const waiting = problem.status === 429 && (retrySecondsLeft ?? 0) > 0;
  return (
    <div
      role="alert"
      className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-950"
    >
      <p className="font-semibold">
        {problem.title || "Request failed"}
        {problem.status ? <span className="ml-2 font-normal text-rose-800">HTTP {problem.status}</span> : null}
      </p>
      {problem.detail ? <p className="mt-1">{problem.detail}</p> : null}
      {problem.status === 422 ? (
        <p className="mt-2">
          Use a new key, or correct the cart or payment token and try again.
        </p>
      ) : null}
      {waiting ? (
        <p className="mt-2 font-medium">Retry in {retrySecondsLeft}s.</p>
      ) : null}
      {problem.status === 429 && (problem.rateLimitLimit || problem.rateLimitRemaining) ? (
        <p className="mt-2 text-rose-800">
          Limit {problem.rateLimitLimit ?? "—"}, remaining {problem.rateLimitRemaining ?? "—"}
          {problem.rateLimitReset ? `, resets at ${problem.rateLimitReset}` : ""}.
          {problem.retryAfterSec != null ? ` Retry-After ${problem.retryAfterSec}s.` : ""}
        </p>
      ) : null}
    </div>
  );
}
