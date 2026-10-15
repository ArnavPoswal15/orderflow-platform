"use client";

export default function Error({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div>
      <h1 className="text-2xl font-semibold text-zinc-950">Something went wrong</h1>
      <p className="mt-2 text-sm text-zinc-600">Something unexpected happened. Try again.</p>
      <button
        type="button"
        onClick={reset}
        className="mt-4 rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-900"
      >
        Try again
      </button>
    </div>
  );
}
