import type {
  AdminStatsView,
  FailureRateView,
  FlashSaleResult,
  OrderPageView,
  OrderView,
  ProblemDetail,
  ProductView,
} from "./types";

export class ApiProblem extends Error {
  readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.detail || problem.title || `Request failed (${problem.status})`);
    this.name = "ApiProblem";
    this.problem = problem;
  }
}

function headerNumber(value: string | null): number | undefined {
  if (value == null || value.trim() === "") return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export async function parseProblem(res: Response): Promise<ProblemDetail> {
  let body: Partial<ProblemDetail> = {};
  const contentType = res.headers.get("content-type") ?? "";
  if (contentType.includes("json")) {
    try {
      body = (await res.json()) as Partial<ProblemDetail>;
    } catch {
      body = {};
    }
  }
  const retryAfter = res.headers.get("Retry-After");
  return {
    type: body.type,
    title: body.title || res.statusText || "Request failed",
    status: typeof body.status === "number" ? body.status : res.status,
    detail: body.detail,
    instance: body.instance,
    retryAfterSec: headerNumber(retryAfter),
    rateLimitLimit: res.headers.get("X-RateLimit-Limit"),
    rateLimitRemaining: res.headers.get("X-RateLimit-Remaining"),
    rateLimitReset: res.headers.get("X-RateLimit-Reset"),
  };
}

async function readOk<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new ApiProblem(await parseProblem(res));
  }
  return (await res.json()) as T;
}

export async function fetchProducts(): Promise<ProductView[]> {
  const res = await fetch("/api/products", { cache: "no-store" });
  return readOk<ProductView[]>(res);
}

export async function fetchOrder(id: string): Promise<OrderView> {
  const res = await fetch(`/api/orders/${id}`, { cache: "no-store" });
  return readOk<OrderView>(res);
}

export async function fetchOrderPage(page: number, size: number): Promise<OrderPageView> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  const res = await fetch(`/api/orders?${params}`, { cache: "no-store" });
  return readOk<OrderPageView>(res);
}

export async function postOrder(
  body: { items: { sku: string; qty: number }[]; paymentToken: string },
  idempotencyKey: string,
): Promise<OrderView> {
  const res = await fetch("/api/orders", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json, application/problem+json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(body),
  });
  return readOk<OrderView>(res);
}

export async function cancelOrder(id: string, reason: string): Promise<OrderView> {
  const res = await fetch(`/api/orders/${id}/cancel`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json, application/problem+json",
    },
    body: JSON.stringify({ reason }),
  });
  return readOk<OrderView>(res);
}

function countMap(value: Record<string, number> | undefined): Record<string, number> {
  if (!value) return {};
  const counts: Record<string, number> = {};
  for (const [key, count] of Object.entries(value)) {
    counts[key] = typeof count === "number" && Number.isFinite(count) ? count : 0;
  }
  return counts;
}

export async function fetchAdminStats(): Promise<AdminStatsView> {
  const res = await fetch("/api/admin/stats", { cache: "no-store" });
  const body = await readOk<Partial<AdminStatsView>>(res);
  return {
    ordersByStatus: countMap(body.ordersByStatus),
    outboxBacklog: typeof body.outboxBacklog === "number" ? body.outboxBacklog : 0,
    dltCounts: countMap(body.dltCounts),
  };
}

export async function fetchFailureRate(): Promise<FailureRateView> {
  const res = await fetch("/api/admin/failure-rate", { cache: "no-store" });
  const body = await readOk<Partial<FailureRateView>>(res);
  return { percent: typeof body.percent === "number" ? body.percent : 0 };
}

export async function putFailureRate(percent: number): Promise<FailureRateView> {
  const res = await fetch("/api/admin/failure-rate", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json, application/problem+json",
    },
    body: JSON.stringify({ percent }),
  });
  const body = await readOk<Partial<FailureRateView>>(res);
  return { percent: typeof body.percent === "number" ? body.percent : percent };
}

export async function postFlashSale(body: {
  sku: string;
  concurrentOrders: number;
}): Promise<FlashSaleResult> {
  const res = await fetch("/api/admin/flash-sale", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json, application/problem+json",
    },
    body: JSON.stringify(body),
  });
  const raw = await readOk<Partial<FlashSaleResult>>(res);
  const orders = Array.isArray(raw.orders) ? raw.orders : [];
  return {
    sku: typeof raw.sku === "string" ? raw.sku : body.sku,
    concurrentOrders: typeof raw.concurrentOrders === "number" ? raw.concurrentOrders : body.concurrentOrders,
    confirmed: typeof raw.confirmed === "number" ? raw.confirmed : 0,
    cancelled: typeof raw.cancelled === "number" ? raw.cancelled : 0,
    orders: orders.map((order) => ({
      orderId: typeof order?.orderId === "string" ? order.orderId : "",
      status: typeof order?.status === "string" ? order.status : "",
      cancelReason: typeof order?.cancelReason === "string" ? order.cancelReason : null,
    })),
  };
}
