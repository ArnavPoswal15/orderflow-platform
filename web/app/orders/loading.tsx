import { OrdersListSkeleton } from "@/components/OrdersListSkeleton";
import { PageHeader } from "@/components/PageHeader";

export default function Loading() {
  return (
    <>
      <PageHeader title="Orders" description="Recent orders, newest first." />
      <OrdersListSkeleton />
    </>
  );
}
