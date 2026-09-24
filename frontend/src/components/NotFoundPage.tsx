import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="flex flex-col gap-2 p-6">
      <h1 className="text-2xl font-semibold text-slate-900">Page not found</h1>
      <p className="text-sm text-slate-600">There is nothing at this address.</p>
      <Link className="text-sm text-slate-700 underline" to="/">
        Back to the dashboard
      </Link>
    </div>
  )
}
