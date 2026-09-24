export function ForbiddenPage() {
  return (
    <div className="flex flex-col gap-2">
      <h1 className="text-2xl font-semibold text-slate-900">Not allowed</h1>
      <p className="text-sm text-slate-600">
        Your account does not have access to this page. If you think it should, ask an administrator.
      </p>
    </div>
  )
}
