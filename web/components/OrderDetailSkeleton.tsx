import { OrdersBackLink } from "./OrdersBackLink";
import { Skeleton } from "./Skeleton";

export function OrderDetailSkeleton() {
  return (
    <div aria-busy="true" aria-live="polite">
      <span className="sr-only">Loading order</span>
      <OrdersBackLink />
      <header className="mb-6">
        <Skeleton className="h-8 w-28 sm:h-9" />
        <Skeleton className="mt-3 h-4 w-full max-w-md" />
      </header>
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <Skeleton className="h-6 w-24 rounded-full" />
            <Skeleton className="h-4 w-20" />
          </div>
          <Skeleton className="h-4 w-16" />
        </div>
        <section className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-6">
          <Skeleton className="h-3 w-20" />
          <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
            <Skeleton className="h-8 w-28 rounded-full" />
            <Skeleton className="h-8 w-32 rounded-full" />
            <Skeleton className="h-8 w-36 rounded-full" />
          </div>
          <Skeleton className="mt-6 h-4 w-16" />
          <div className="mt-3 space-y-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        </section>
        <section className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-6">
          <Skeleton className="h-3 w-12" />
          <div className="mt-4 space-y-4">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        </section>
        <Skeleton className="h-10 w-32 rounded-lg" />
      </div>
    </div>
  );
}
