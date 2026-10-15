export type ProductView = {
  sku: string;
  name: string;
  priceCents: number;
  available: number;
};

export type OrderLine = {
  sku: string;
  qty: number;
  unitPriceCents: number;
};

export type HistoryEntry = {
  type: string;
  data: Record<string, unknown> | null;
  at: string;
};

export type OrderView = {
  id: string;
  status: string;
  totalCents: number;
  paymentToken: string | null;
  cancelReason: string | null;
  items: OrderLine[];
  history: HistoryEntry[];
  createdAt: string;
  updatedAt: string;
};

export type OrderPageView = {
  content: OrderView[];
  page: number;
  size: number;
  totalElements: number;
};

export type EventEnvelope = {
  eventId: string;
  type: string;
  version: number;
  orderId: string;
  occurredAt: string | number;
  traceId: string;
  payload: unknown;
};

export type AdminStatsView = {
  ordersByStatus: Record<string, number>;
  outboxBacklog: number;
  dltCounts: Record<string, number>;
};

export type FailureRateView = {
  percent: number;
};

export type FlashSaleOrder = {
  orderId: string;
  status: string;
  cancelReason: string | null;
};

export type FlashSaleResult = {
  sku: string;
  concurrentOrders: number;
  confirmed: number;
  cancelled: number;
  orders: FlashSaleOrder[];
};

export type ProblemDetail = {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  retryAfterSec?: number;
  rateLimitLimit?: string | null;
  rateLimitRemaining?: string | null;
  rateLimitReset?: string | null;
};
