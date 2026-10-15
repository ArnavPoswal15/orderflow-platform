import type { NextConfig } from "next";

const orderServiceUrl = (
  process.env.ORDER_SERVICE_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

const nextConfig: NextConfig = {
  output: "standalone",
  devIndicators: false,
  cacheComponents: true,
  partialPrefetching: true,
  // Gzip compression buffers small chunks. SSE needs them flushed immediately.
  compress: false,
  experimental: {
    // Rewrite proxy defaults to 30s, which cuts off a live order stream.
    proxyTimeout: 1_800_000,
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    // `/api/stream/*` is a route handler. A catch-all rewrite is applied
    // before dynamic routes and would buffer the event stream.
    return [
      { source: "/api/products", destination: `${orderServiceUrl}/api/products` },
      { source: "/api/orders", destination: `${orderServiceUrl}/api/orders` },
      { source: "/api/orders/:path*", destination: `${orderServiceUrl}/api/orders/:path*` },
      { source: "/api/admin", destination: `${orderServiceUrl}/api/admin` },
      { source: "/api/admin/:path*", destination: `${orderServiceUrl}/api/admin/:path*` },
    ];
  },
};

export default nextConfig;
