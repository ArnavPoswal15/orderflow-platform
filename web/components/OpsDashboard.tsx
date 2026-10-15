"use client";

import { useEffect, useState } from "react";
import { ApiProblem, fetchAdminStats, fetchProducts } from "@/lib/api";
import type { AdminStatsView, ProblemDetail, ProductView } from "@/lib/types";
import { FailureRateControl } from "./FailureRateControl";
import { FlashSalePanel } from "./FlashSalePanel";
import { OpsLiveFeed } from "./OpsLiveFeed";
import { OpsStatusPanel } from "./OpsStatusPanel";
import { OpsStockTable } from "./OpsStockTable";
import { ProblemAlert } from "./ProblemAlert";

const POLL_MS = 2000;

export function OpsDashboard() {
  const [products, setProducts] = useState<ProductView[] | null>(null);
  const [stats, setStats] = useState<AdminStatsView | null>(null);
  const [productProblem, setProductProblem] = useState<ProblemDetail | null>(null);
  const [statsProblem, setStatsProblem] = useState<ProblemDetail | null>(null);
  const [productError, setProductError] = useState<string | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let inFlight = false;
    let hadProducts = false;
    let hadStats = false;

    const tick = async () => {
      if (inFlight) return;
      inFlight = true;
      const [productResult, statsResult] = await Promise.allSettled([fetchProducts(), fetchAdminStats()]);
      inFlight = false;
      if (cancelled) return;

      if (productResult.status === "fulfilled") {
        hadProducts = true;
        setProducts(productResult.value);
        setProductProblem(null);
        setProductError(null);
      } else if (productResult.reason instanceof ApiProblem) {
        setProductProblem(productResult.reason.problem);
        setProductError(null);
      } else {
        setProductProblem(null);
        setProductError(
          hadProducts
            ? "Stock refresh failed. Showing the last table."
            : "Could not load stock. Check that order-service is running.",
        );
      }

      if (statsResult.status === "fulfilled") {
        hadStats = true;
        setStats(statsResult.value);
        setStatsProblem(null);
        setStatsError(null);
      } else if (statsResult.reason instanceof ApiProblem) {
        setStatsProblem(statsResult.reason.problem);
        setStatsError(null);
      } else {
        setStatsProblem(null);
        setStatsError(
          hadStats
            ? "Counter refresh failed. Showing the last counts."
            : "Could not load counters. Check that order-service is running.",
        );
      }
    };

    void tick();
    const id = window.setInterval(() => {
      void tick();
    }, POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, []);

  return (
    <div className="space-y-6">
      <div className="grid gap-6 lg:grid-cols-2">
        <FailureRateControl />
        <FlashSalePanel products={products ?? []} />
      </div>

      <OpsLiveFeed />

      <p className="text-sm text-zinc-500">Stock and counters refresh about every 2 seconds.</p>

      {statsProblem ? <ProblemAlert problem={statsProblem} /> : null}
      {statsError ? (
        <p className="text-sm text-amber-800" role="status">
          {statsError}
        </p>
      ) : null}
      {stats ? <OpsStatusPanel stats={stats} /> : statsProblem || statsError ? null : (
        <p className="text-sm text-zinc-500">Loading counters…</p>
      )}

      {productProblem ? <ProblemAlert problem={productProblem} /> : null}
      {productError ? (
        <p className="text-sm text-amber-800" role="status">
          {productError}
        </p>
      ) : null}
      {products ? <OpsStockTable products={products} /> : productProblem || productError ? null : (
        <p className="text-sm text-zinc-500">Loading stock…</p>
      )}
    </div>
  );
}
