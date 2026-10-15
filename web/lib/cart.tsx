"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useSyncExternalStore,
} from "react";
import type { ProductView } from "./types";

const STORAGE_KEY = "shop.cart";

export type CartLine = {
  sku: string;
  name: string;
  priceCents: number;
  qty: number;
};

type CartContextValue = {
  lines: CartLine[];
  ready: boolean;
  count: number;
  subtotalCents: number;
  addItem: (product: ProductView) => void;
  setQty: (sku: string, qty: number) => void;
  remove: (sku: string) => void;
  clear: () => void;
};

const CartContext = createContext<CartContextValue | null>(null);
const SERVER_SNAPSHOT = Symbol("cart-server");
type CartSnapshot = string | typeof SERVER_SNAPSHOT;
const listeners = new Set<() => void>();

function emitCart() {
  for (const listener of listeners) listener();
}

function subscribeCart(onStoreChange: () => void) {
  listeners.add(onStoreChange);
  return () => listeners.delete(onStoreChange);
}

function cartSnapshot(): CartSnapshot {
  return window.localStorage.getItem(STORAGE_KEY) ?? "";
}

function serverCartSnapshot(): CartSnapshot {
  return SERVER_SNAPSHOT;
}

function persistLines(lines: CartLine[]) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(lines));
  emitCart();
}

function integerCents(priceCents: number, qty: number): number {
  if (!Number.isSafeInteger(priceCents) || !Number.isSafeInteger(qty)) return 0;
  const total = priceCents * qty;
  return Number.isSafeInteger(total) ? total : 0;
}

function parseStored(raw: string | null): CartLine[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw) as CartLine[];
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (line) =>
        line &&
        typeof line.sku === "string" &&
        typeof line.name === "string" &&
        Number.isSafeInteger(line.priceCents) &&
        Number.isSafeInteger(line.qty) &&
        line.qty > 0,
    );
  } catch {
    return [];
  }
}

export function CartProvider({ children }: { children: React.ReactNode }) {
  const raw = useSyncExternalStore(subscribeCart, cartSnapshot, serverCartSnapshot);
  const ready = raw !== SERVER_SNAPSHOT;
  const lines = useMemo(() => (typeof raw === "string" ? parseStored(raw) : []), [raw]);

  const addItem = useCallback((product: ProductView) => {
    if (!Number.isSafeInteger(product.priceCents)) return;
    const current = parseStored(window.localStorage.getItem(STORAGE_KEY));
    const existing = current.find((line) => line.sku === product.sku);
    const next = !existing
      ? [
          ...current,
          {
            sku: product.sku,
            name: product.name,
            priceCents: product.priceCents,
            qty: 1,
          },
        ]
      : current.map((line) =>
          line.sku === product.sku ? { ...line, qty: line.qty + 1 } : line,
        );
    persistLines(next);
  }, []);

  const setQty = useCallback((sku: string, qty: number) => {
    const current = parseStored(window.localStorage.getItem(STORAGE_KEY));
    const next =
      !Number.isSafeInteger(qty) || qty <= 0
        ? current.filter((line) => line.sku !== sku)
        : current.map((line) => (line.sku === sku ? { ...line, qty } : line));
    persistLines(next);
  }, []);

  const remove = useCallback((sku: string) => {
    persistLines(parseStored(window.localStorage.getItem(STORAGE_KEY)).filter((line) => line.sku !== sku));
  }, []);

  const clear = useCallback(() => persistLines([]), []);

  const value = useMemo<CartContextValue>(() => {
    const count = lines.reduce((sum, line) => sum + line.qty, 0);
    const subtotalCents = lines.reduce((sum, line) => sum + integerCents(line.priceCents, line.qty), 0);
    return { lines, ready, count, subtotalCents, addItem, setQty, remove, clear };
  }, [lines, ready, addItem, setQty, remove, clear]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartContextValue {
  const ctx = useContext(CartContext);
  if (!ctx) {
    throw new Error("useCart must be used within CartProvider");
  }
  return ctx;
}
