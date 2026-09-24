import { useHealthQuery } from '@/api/queries/health'
import { Button } from '@/components/ui/button'

export function DashboardPage() {
  const { data, isLoading } = useHealthQuery()

  const status = isLoading ? 'Checking...' : (data?.status ?? 'DOWN')
  const isUp = status === 'UP'

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>
      <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-4">
        <span
          className={`h-2.5 w-2.5 rounded-full ${isUp ? 'bg-emerald-500' : 'bg-red-500'}`}
          aria-hidden
        />
        <span className="text-sm text-slate-600">Backend status: {status}</span>
        <Button variant="outline" size="sm" className="ml-auto">
          Refresh
        </Button>
      </div>
    </div>
  )
}
