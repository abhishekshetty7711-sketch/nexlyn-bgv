/** Placeholder for screens that arrive in a later build phase (CLAUDE.md §15). */
export function ComingSoonPage({ title }: { title: string }) {
  return (
    <div className="flex flex-col gap-2">
      <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
      <p className="text-sm text-slate-600">This section is being built and will be available soon.</p>
    </div>
  )
}
