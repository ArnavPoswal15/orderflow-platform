"use client";

import { useCart, useHydrated } from "@/lib/cart";

export function CartCount({ active }: { active: boolean }) {
  const { count } = useCart();
  const visible = useHydrated();

  if (!visible || count <= 0) return null;

  return (
    <span
      className={`ml-2 inline-flex min-w-5 items-center justify-center rounded-full px-1.5 text-xs tabular-nums ${
        active ? "bg-white text-zinc-900" : "bg-zinc-900 text-white"
      }`}
    >
      {count}
    </span>
  );
}
