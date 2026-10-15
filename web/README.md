# Corner Market storefront

Next.js App Router UI for the order platform. REST `/api/products` and `/api/orders` are rewrites to order-service. `/api/stream/*` is a route handler so the event stream is not buffered.

```bash
cd web && npm run dev
```

`ORDER_SERVICE_URL` defaults to `http://localhost:8080`. Docker Compose builds the image with `http://order-service:8080` and serves the app on port 3000 (`node server.js` from the standalone output).
