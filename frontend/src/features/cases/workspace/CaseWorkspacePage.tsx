import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useBlocker, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '@/api/httpClient'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/features/auth/AuthContext'
import { useCase, useProgress } from '../api'
import { useChecks } from '../checks/api'
import { formatDate } from '../format'
import { isSectionKey, LIFECYCLE_LABELS, type SectionKey } from '../types'
import { AssignmentsPanel } from './AssignmentsPanel'
import { CandidateSection } from './CandidateSection'
import { ChecksSection } from './ChecksSection'
import { DirtyGuardContext } from './dirtyGuard'
import { GenerateSection } from './GenerateSection'
import { OverviewSection } from './OverviewSection'
import { PeriodSection } from './PeriodSection'
import { RemarksSection } from './RemarksSection'
import { ReportInfoSection } from './ReportInfoSection'
import { SectionNavigator } from './SectionNavigator'
import { SettingsSection } from './SettingsSection'

/**
 * One page for one case (CLAUDE.md section 7): the section list on the left, the chosen section on the
 * right. Leaving a section, another page, or the browser tab with unsaved typing asks first.
 */
export function CaseWorkspacePage() {
  const { id = '' } = useParams()
  const [search, setSearch] = useSearchParams()
  const { hasPermission } = useAuth()
  const queryClient = useQueryClient()
  const caseQuery = useCase(id)
  const progress = useProgress(id)
  const checks = useChecks(id)

  const requested = search.get('section')
  const section: SectionKey = isSectionKey(requested) ? requested : 'report-info'
  const checkParam = section === 'checks' ? search.get('check') : null

  // ---- unsaved-changes guard ---------------------------------------------------------------
  const dirty = useRef(false)
  const [pending, setPending] = useState<{ section: SectionKey; check: string | null } | null>(null)
  const guard = useMemo(() => ({ setDirty: (value: boolean) => void (dirty.current = value) }), [])

  const blocker = useBlocker(({ currentLocation, nextLocation }) => dirty.current && currentLocation.pathname !== nextLocation.pathname)

  useEffect(() => {
    const warnOnClose = (event: BeforeUnloadEvent) => {
      if (dirty.current) {
        event.preventDefault()
      }
    }
    window.addEventListener('beforeunload', warnOnClose)
    return () => window.removeEventListener('beforeunload', warnOnClose)
  }, [])

  const goTo = useCallback(
    (next: SectionKey, check: string | null = null) => {
      const target = next === 'checks' ? check : null
      if (next === section && target === checkParam) {
        return
      }
      if (dirty.current) {
        setPending({ section: next, check: target })
        return
      }
      setSearch(target ? { section: next, check: target } : { section: next })
    },
    [section, checkParam, setSearch],
  )

  const reload = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['case', id] })
    void queryClient.invalidateQueries({ queryKey: ['case-progress', id] })
    void queryClient.invalidateQueries({ queryKey: ['case-validation', id] })
  }, [queryClient, id])

  // ---- loading and errors ------------------------------------------------------------------
  if (caseQuery.isLoading) {
    return <Spinner />
  }
  if (caseQuery.isError || !caseQuery.data) {
    const status = caseQuery.error instanceof ApiError ? caseQuery.error.status : 0
    return (
      <div className="flex flex-col gap-3">
        <Alert variant="error">
          {status === 403 || status === 404 ? 'This case does not exist, or you are not allowed to open it.' : describeError(caseQuery.error)}
        </Alert>
        <Link to="/cases" className="text-sm text-slate-700 underline">
          Back to cases
        </Link>
      </div>
    )
  }

  const caseView = caseQuery.data
  const canEdit = caseView.editable && hasPermission('CASE_UPDATE')
  const props = { caseView, canEdit, onReload: reload }

  return (
    <DirtyGuardContext.Provider value={guard}>
      <div className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <Link to="/cases" className="text-sm text-slate-500 underline">
            Cases
          </Link>
          <span className="text-slate-300">/</span>
          <h1 className="text-xl font-semibold text-slate-900">{caseView.reportId}</h1>
          <Badge tone={caseView.lifecycle === 'FINALIZED' || caseView.lifecycle === 'APPROVED' ? 'green' : caseView.lifecycle === 'CHANGES_REQUESTED' ? 'amber' : 'neutral'}>
            {LIFECYCLE_LABELS[caseView.lifecycle]}
          </Badge>
          <span className="text-sm text-slate-500">
            {caseView.client.name}
            {caseView.candidate.fullName ? ` · ${caseView.candidate.fullName}` : ''}
            {` · issued ${formatDate(caseView.issueDate, caseView.settings.dateFormat)}`}
          </span>
        </div>

        {!caseView.editable && (
          <Alert variant="info">
            This case is {LIFECYCLE_LABELS[caseView.lifecycle].toLowerCase()} and is locked for editing. You can still read everything.
          </Alert>
        )}
        {caseView.editable && !hasPermission('CASE_UPDATE') && (
          <Alert variant="info">You can read this case but not change it.</Alert>
        )}
        {caseView.reviewComment && caseView.lifecycle === 'CHANGES_REQUESTED' && (
          <Alert variant="warning">Reviewer&apos;s comment: {caseView.reviewComment}</Alert>
        )}

        <div className="grid gap-4 lg:grid-cols-[16rem_1fr]">
          <aside className="flex flex-col gap-4">
            <Card>
              <SectionNavigator
                progress={progress.data}
                current={section}
                onSelect={(key) => goTo(key)}
                checks={checks.data ?? []}
                currentCheckId={checkParam}
                onSelectCheck={(checkId) => goTo('checks', checkId)}
              />
              <AssignmentsPanel caseView={caseView} />
            </Card>
          </aside>
          <Card className="min-w-0">
            {section === 'report-info' && <ReportInfoSection {...props} />}
            {section === 'candidate' && <CandidateSection {...props} />}
            {section === 'verification-period' && <PeriodSection {...props} />}
            {section === 'checks' && <ChecksSection caseView={caseView} checkId={checkParam} onSelectCheck={(checkId) => goTo('checks', checkId)} />}
            {section === 'overview' && <OverviewSection {...props} />}
            {section === 'remarks' && <RemarksSection {...props} />}
            {section === 'settings' && <SettingsSection {...props} />}
            {section === 'generate' && <GenerateSection caseView={caseView} onGoToSection={goTo} />}
          </Card>
        </div>

        {pending && (
          <UnsavedDialog
            onStay={() => setPending(null)}
            onDiscard={() => {
              dirty.current = false
              setSearch(pending.check ? { section: pending.section, check: pending.check } : { section: pending.section })
              setPending(null)
            }}
          />
        )}
        {blocker.state === 'blocked' && (
          <UnsavedDialog
            onStay={() => blocker.reset()}
            onDiscard={() => {
              dirty.current = false
              blocker.proceed()
            }}
          />
        )}
      </div>
    </DirtyGuardContext.Provider>
  )
}

function UnsavedDialog({ onStay, onDiscard }: { onStay: () => void; onDiscard: () => void }) {
  return (
    <Dialog title="Unsaved changes" onClose={onStay}>
      <p className="mb-4 text-sm text-slate-600">You have changes in this section that are not saved. If you leave now they will be lost.</p>
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onDiscard}>
          Discard changes
        </Button>
        <Button onClick={onStay}>Stay and keep editing</Button>
      </div>
    </Dialog>
  )
}
