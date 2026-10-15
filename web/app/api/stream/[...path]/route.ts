import { getOrderServiceUrl } from "@/lib/server-api";

type StreamContext = { params: Promise<{ path: string[] }> };

export async function GET(request: Request, context: StreamContext) {
  const { path } = await context.params;
  const target = new URL(`${getOrderServiceUrl()}/api/stream/${path.join("/")}`);
  target.search = new URL(request.url).search;

  const upstream = await fetch(target, {
    headers: { Accept: "text/event-stream" },
    cache: "no-store",
    signal: request.signal,
  });

  const headers = new Headers();
  headers.set("Content-Type", upstream.headers.get("content-type") ?? "text/event-stream");
  headers.set("Cache-Control", "no-cache, no-transform");
  headers.set("Connection", "keep-alive");
  headers.set("X-Accel-Buffering", "no");

  return new Response(upstream.body, {
    status: upstream.status,
    headers,
  });
}
