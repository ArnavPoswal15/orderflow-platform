import "server-only";

import { ApiProblem, parseProblem } from "./api";
import type { OrderPageView, OrderView, ProductView } from "./types";

/** Server-side base URL for RSC fetches. Client code must call relative `/api`. */
export function getOrderServiceUrl(): string {
  return (process.env.ORDER_SERVICE_URL ?? "http://localhost:8080").replace(/\/$/, "");
}

async function readOk<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new ApiProblem(await parseProblem(res));
  }
  return (await res.json()) as T;
}

export async function getProducts(): Promise<ProductView[]> {
  const res = await fetch(`${getOrderServiceUrl()}/api/products`, { cache: "no-store" });
  return readOk<ProductView[]>(res);
}

export async function getOrderPage(page: number, size: number): Promise<OrderPageView> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  const res = await fetch(`${getOrderServiceUrl()}/api/orders?${params}`, { cache: "no-store" });
  return readOk<OrderPageView>(res);
}

export async function getOrder(id: string): Promise<OrderView | null> {
  const res = await fetch(`${getOrderServiceUrl()}/api/orders/${id}`, { cache: "no-store" });
  if (res.status === 404) return null;
  return readOk<OrderView>(res);
}
