import { Skeleton } from "./Skeleton";

export function OrdersListSkeleton() {
  return (
    <div aria-busy="true" aria-live="polite" className="space-y-3">
      <span className="sr-only">Loading orders</span>
      <ul className="space-y-3 md:hidden">
        {["a", "b", "c", "d"].map((key) => (
          <li key={key} className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
            <Skeleton className="h-3 w-56" />
            <div className="mt-3 flex items-center justify-between gap-3">
              <Skeleton className="h-6 w-24 rounded-full" />
              <Skeleton className="h-4 w-14" />
            </div>
            <Skeleton className="mt-3 h-3 w-32" />
          </li>
        ))}
      </ul>
      <div className="hidden overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm md:block">
        <div className="border-b border-zinc-200 bg-zinc-50 px-4 py-3">
          <Skeleton className="h-3 w-full max-w-lg" />
        </div>
        <div className="divide-y divide-zinc-100">
          {["a", "b", "c", "d", "e"].map((key) => (
            <div key={key} className="grid grid-cols-4 gap-4 px-4 py-4">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-6 w-24 rounded-full" />
              <Skeleton className="h-4 w-16" />
              <Skeleton className="h-4 w-28" />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
