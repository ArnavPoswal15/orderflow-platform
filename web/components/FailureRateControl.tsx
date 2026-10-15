"use client";

import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { ApiProblem, fetchFailureRate, putFailureRate } from "@/lib/api";
import type { ProblemDetail } from "@/lib/types";
import { ProblemAlert } from "./ProblemAlert";

const DEBOUNCE_MS = 300;

function clampPercent(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(100, Math.max(0, Math.round(value)));
}

export function FailureRateControl() {
  const [percent, setPercent] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [touched, setTouched] = useState(false);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const latest = useRef(0);
  const touchedRef = useRef(false);
  const alive = useRef(true);
  const inFlight = useRef(false);
  const queued = useRef(false);
  const timer = useRef<number | null>(null);

  async function flush() {
    if (!alive.current) return;
    if (inFlight.current) {
      queued.current = true;
      return;
    }
    const value = latest.current;
    inFlight.current = true;
    queued.current = false;
    setSaving(true);
    try {
      await putFailureRate(value);
      if (alive.current && latest.current === value) setProblem(null);
    } catch (error) {
      if (alive.current) {
        if (error instanceof ApiProblem) setProblem(error.problem);
        else {
          setProblem({
            status: 0,
            title: "Could not update the failure rate",
            detail: "Could not reach the shop. Try again.",
          });
        }
      }
    } finally {
      inFlight.current = false;
      if (!alive.current) return;
      setSaving(false);
      if (queued.current || latest.current !== value) {
        queued.current = false;
        if (timer.current != null) {
          window.clearTimeout(timer.current);
          timer.current = null;
        }
        void flush();
      }
    }
  }

  useEffect(() => {
    alive.current = true;
    void (async () => {
      try {
        const view = await fetchFailureRate();
        if (!alive.current) return;
        setLoading(false);
        if (touchedRef.current) return;
        const next = clampPercent(view.percent);
        latest.current = next;
        setPercent(next);
        setProblem(null);
      } catch (error) {
        if (!alive.current) return;
        setLoading(false);
        if (error instanceof ApiProblem) setProblem(error.problem);
        else {
          setProblem({
            status: 0,
            title: "Could not load the failure rate",
            detail: "Could not reach the shop. Try again.",
          });
        }
      }
    })();
    return () => {
      alive.current = false;
      if (timer.current != null) {
        window.clearTimeout(timer.current);
        timer.current = null;
      }
    };
  }, []);

  function onChange(event: ChangeEvent<HTMLInputElement>) {
    const next = clampPercent(Number(event.target.value));
    touchedRef.current = true;
    setTouched(true);
    latest.current = next;
    setPercent(next);
    if (timer.current != null) window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      timer.current = null;
      void flush();
    }, DEBOUNCE_MS);
  }

  const status = saving ? "Saving…" : loading ? "Loading the current rate…" : "Takes effect immediately.";

  return (
    <section className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-6">
      <div className="flex items-baseline justify-between gap-3">
        <h2>
          <label
            htmlFor="failure-rate"
            className="text-sm font-semibold uppercase tracking-wide text-zinc-500"
          >
            Payment failure rate
          </label>
        </h2>
        <p className="text-3xl font-semibold tabular-nums text-zinc-950" aria-hidden="true">
          {loading && !touched ? "…" : `${percent}%`}
        </p>
      </div>
      <input
        id="failure-rate"
        type="range"
        min={0}
        max={100}
        step={1}
        value={percent}
        disabled={loading && !touched}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-valuetext={`${percent} percent`}
        aria-describedby="failure-rate-status"
        onChange={onChange}
        className="mt-4 w-full accent-zinc-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900 disabled:opacity-60"
      />
      <div className="mt-1 flex justify-between text-xs text-zinc-500">
        <span>0%</span>
        <span>100%</span>
      </div>
      <p id="failure-rate-status" className="mt-3 text-sm text-zinc-500">
        {status}
      </p>
      {problem ? (
        <div className="mt-4">
          <ProblemAlert problem={problem} />
        </div>
      ) : null}
    </section>
  );
}
