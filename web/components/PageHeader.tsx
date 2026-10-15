export function PageHeader({
  title,
  description,
}: {
  title: string;
  description?: string;
}) {
  return (
    <header className="mb-6">
      <h1 className="text-2xl font-semibold tracking-tight text-zinc-950 sm:text-3xl">{title}</h1>
      {description ? <p className="mt-2 max-w-2xl text-sm text-zinc-600 sm:text-base">{description}</p> : null}
    </header>
  );
}
