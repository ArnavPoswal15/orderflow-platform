import Link from "next/link";

export function OrdersBackLink() {
  return (
    <Link
      href="/orders"
      className="mb-4 inline-flex items-center text-sm font-medium text-zinc-700 underline-offset-4 hover:text-zinc-950 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
    >
      ← Orders
    </Link>
  );
}
