import { Suspense } from "react";
import { connection } from "next/server";
import { PageHeader } from "@/components/PageHeader";
import { ProblemAlert } from "@/components/ProblemAlert";
import { ProductGrid } from "@/components/ProductGrid";
import { ApiProblem } from "@/lib/api";
import { getProducts } from "@/lib/server-api";
import type { ProblemDetail, ProductView } from "@/lib/types";

export const metadata = {
  title: "Shop",
};

export default function HomePage() {
  return (
    <>
      <PageHeader
        title="Shop"
        description="Milk, eggs, and whatever is left on the shelf. Stock counts stay live."
      />
      <Suspense fallback={<p className="text-sm text-zinc-500">Loading products…</p>}>
        <Catalog />
      </Suspense>
    </>
  );
}

async function Catalog() {
  await connection();
  const loaded = await loadProducts();
  if (loaded.problem) return <ProblemAlert problem={loaded.problem} />;
  if (!loaded.products) {
    return (
      <p className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
        Could not load the catalog. Check that order-service is running.
      </p>
    );
  }
  return <ProductGrid initialProducts={loaded.products} />;
}

async function loadProducts(): Promise<{ products: ProductView[] | null; problem: ProblemDetail | null }> {
  try {
    return { products: await getProducts(), problem: null };
  } catch (error) {
    if (error instanceof ApiProblem) return { products: null, problem: error.problem };
    return { products: null, problem: null };
  }
}
