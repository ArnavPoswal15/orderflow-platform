import { Suspense } from "react";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import { OrderLivePanel } from "@/components/OrderLivePanel";
import { PageHeader } from "@/components/PageHeader";
import { ProblemAlert } from "@/components/ProblemAlert";
import { ApiProblem } from "@/lib/api";
import { getOrder } from "@/lib/server-api";
import type { OrderView, ProblemDetail } from "@/lib/types";

export const metadata = {
  title: "Order",
};

export default function OrderPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={<p className="text-sm text-zinc-500">Loading order…</p>}>
      <OrderDetail params={params} />
    </Suspense>
  );
}

async function OrderDetail({ params }: { params: Promise<{ id: string }> }) {
  await connection();
  const { id } = await params;
  const loaded = await loadOrder(id);
  if (loaded.problem) {
    return (
      <>
        <PageHeader title="Order" description={id} />
        <ProblemAlert problem={loaded.problem} />
      </>
    );
  }
  if (!loaded.order) notFound();
  return (
    <>
      <PageHeader title="Order" description={loaded.order.id} />
      <OrderLivePanel key={loaded.order.id} initial={loaded.order} />
    </>
  );
}

async function loadOrder(id: string): Promise<{ order: OrderView | null; problem: ProblemDetail | null }> {
  try {
    return { order: await getOrder(id), problem: null };
  } catch (error) {
    if (error instanceof ApiProblem) return { order: null, problem: error.problem };
    throw error;
  }
}
