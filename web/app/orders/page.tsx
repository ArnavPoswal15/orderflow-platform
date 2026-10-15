import { Suspense } from "react";
import { connection } from "next/server";
import { OrdersList } from "@/components/OrdersList";
import { PageHeader } from "@/components/PageHeader";
import { ProblemAlert } from "@/components/ProblemAlert";
import { ApiProblem } from "@/lib/api";
import { getOrderPage } from "@/lib/server-api";
import type { OrderPageView, ProblemDetail } from "@/lib/types";

export const metadata = {
  title: "Orders",
};

export default function OrdersPage() {
  return (
    <>
      <PageHeader title="Orders" description="Recent orders, newest first." />
      <Suspense fallback={<p className="text-sm text-zinc-500">Loading orders…</p>}>
        <OrdersLoader />
      </Suspense>
    </>
  );
}

async function OrdersLoader() {
  await connection();
  const loaded = await loadOrders();
  if (loaded.problem) return <ProblemAlert problem={loaded.problem} />;
  if (!loaded.page) {
    return (
      <p className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
        Could not load orders. Check that order-service is running.
      </p>
    );
  }
  return <OrdersList initial={loaded.page} />;
}

async function loadOrders(): Promise<{ page: OrderPageView | null; problem: ProblemDetail | null }> {
  try {
    return { page: await getOrderPage(0, 20), problem: null };
  } catch (error) {
    if (error instanceof ApiProblem) return { page: null, problem: error.problem };
    return { page: null, problem: null };
  }
}
