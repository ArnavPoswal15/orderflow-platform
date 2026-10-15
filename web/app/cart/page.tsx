import { CartPanel } from "@/components/CartPanel";
import { PageHeader } from "@/components/PageHeader";

export const metadata = {
  title: "Cart",
};

export default function CartPage() {
  return (
    <>
      <PageHeader title="Cart" description="Quantities stay in this browser until you check out." />
      <CartPanel />
    </>
  );
}
