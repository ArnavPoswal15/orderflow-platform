import { CheckoutForm } from "@/components/CheckoutForm";
import { PageHeader } from "@/components/PageHeader";

export const metadata = {
  title: "Checkout",
};

export default function CheckoutPage() {
  return (
    <>
      <PageHeader
        title="Checkout"
        description="The same Idempotency-Key is reused until the cart or payment token changes."
      />
      <CheckoutForm />
    </>
  );
}
