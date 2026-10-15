import { OpsDashboard } from "@/components/OpsDashboard";
import { PageHeader } from "@/components/PageHeader";

export const metadata = {
  title: "Ops",
};

export default function OpsPage() {
  return (
    <>
      <PageHeader
        title="Ops"
        description="Live events, stock, status counts, and the flash-sale demo."
      />
      <OpsDashboard />
    </>
  );
}
