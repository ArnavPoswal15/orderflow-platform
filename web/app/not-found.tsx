import Link from "next/link";

export default function NotFound() {
  return (
    <div>
      <h1 className="text-2xl font-semibold text-zinc-950">Not found</h1>
      <p className="mt-2 text-sm text-zinc-600">That page or order does not exist.</p>
      <Link href="/" className="mt-4 inline-block text-sm font-medium text-zinc-950 underline">
        Back to the shop
      </Link>
    </div>
  );
}
