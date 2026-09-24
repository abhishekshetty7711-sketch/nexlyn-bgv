import { Alert } from '@/components/ui/alert'
import { useProgress } from '../api'

/** Section 4 arrives with the checks phase; until then it only says so and shows the (empty) counts. */
export function ChecksSection({ caseId }: { caseId: string }) {
  const progress = useProgress(caseId)
  return (
    <div className="flex flex-col gap-4">
      <div className="border-b border-slate-200 pb-3">
        <h2 className="text-lg font-semibold text-slate-900">4. Checks</h2>
        <p className="text-sm text-slate-500">One card per verification: identity, address, education, employment, court and more.</p>
      </div>
      <Alert variant="info">Adding and editing checks is the next build phase. Until then a case cannot be submitted, because it needs at least one check.</Alert>
      {progress.data && <p className="text-sm text-slate-600">Checks on this case: {progress.data.totalChecks}</p>}
    </div>
  )
}
