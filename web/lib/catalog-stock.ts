"use client";

import { useEffect, useState } from "react";
import { fetchProducts } from "./api";

export function useCatalogStock() {
  const [bySku, setBySku] = useState<Map<string, number>>(new Map());

  useEffect(() => {
    let cancelled = false;
    fetchProducts()
      .then((products) => {
        if (cancelled) return;
        setBySku(new Map(products.map((product) => [product.sku, product.available])));
      })
      .catch(() => {
        // Leave the map empty so the cart still works if the catalog request fails.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return bySku;
}

export function linesOverStock<T extends { sku: string; qty: number }>(
  lines: T[],
  bySku: Map<string, number>,
): T[] {
  return lines.filter((line) => {
    const available = bySku.get(line.sku);
    return available != null && line.qty > available;
  });
}
