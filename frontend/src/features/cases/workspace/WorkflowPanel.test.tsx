import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes, type FakeHandler } from '@/test/testUtils'
import type { ReportVersion } from '../../reports/api'
import { caseFixture } from '../testFixtures'
import type { CaseView, HistoryEntry, WorkflowActions } from '../types'
import { WorkflowPanel } from './WorkflowPanel'

const NO_ACTIONS: WorkflowActions = { canSubmit: false, canApprove: false, canRequestChanges: false, canFinalize: false, canReopen: false }

const withActions = (actions: Partial<WorkflowActions>, overrides: Partial<CaseView> = {}): CaseView => {
  const base = caseFixture()
  return caseFixture({ ...overrides, workflow: { ...base.workflow, ...(overrides.workflow ?? {}), actions: { ...NO_ACTIONS, ...actions } } })
}

const draftVersion = (version: number, generatedAt: string): ReportVersion => ({
  version,
  kind: 'DRAFT',
  sizeBytes: 1000,
  pageCount: 4,
  encrypted: false,
  generatedByName: 'Rex Reviewer',
  generatedAt,
  finalizedAt: null,
  warnings: [],
})

function show(caseView: CaseView, options: { hasErrors?: boolean; warningCount?: number; handlers?: Record<string, FakeHandler>; versions?: ReportVersion[]; history?: HistoryEntry[] } = {}) {
  const fake = mockFetch({
    'GET /api/cases/c-1/history': () => ({ body: options.history ?? [] }),
    'GET /api/cases/c-1/reports': () => ({ body: options.versions ?? [] }),
    ...options.handlers,
  })
  renderRoutes(
    [{ path: '/', element: <WorkflowPanel caseView={caseView} hasErrors={options.hasErrors ?? false} warningCount={options.warningCount ?? 0} /> }],
    { auth: fakeAuth() },
  )
  return fake
}

const posts = (fake: ReturnType<typeof mockFetch>, path: string) =>
  fake.mock.calls.filter(([url, init]) => url === `/api/cases/c-1/${path}` && init?.method === 'POST').map(([, init]) => JSON.parse(init!.body as string) as unknown)

describe('WorkflowPanel', () => {
  it('offers only the steps the server says this admin may take', () => {
    show(withActions({ canSubmit: true }))
    expect(screen.getByRole('button', { name: 'Submit for review' })).toBeInTheDocument()
    for (const name of ['Approve', 'Request changes', 'Finalize report', 'Reopen for changes']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument()
    }
  })

  it('tells the preparer why they cannot approve their own case', () => {
    show(withActions({}, { lifecycle: 'IN_REVIEW', editable: false }))
    expect(screen.getByText(/Whoever prepared or submitted a case cannot approve it/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  })

  it('shows who submitted, approved and finalized, and the reviewer comment', () => {
    show(
      withActions({}, {
        lifecycle: 'APPROVED',
        reviewComment: 'Looks good.',
        workflow: {
          submittedAt: '2026-09-24T08:00:00Z',
          submittedByName: 'Pia Preparer',
          reviewedAt: '2026-09-24T09:00:00Z',
          reviewedByName: 'Rex Reviewer',
          approvedAt: '2026-09-24T09:00:00Z',
          finalizedAt: null,
          finalizedByName: null,
          actions: NO_ACTIONS,
        },
      }),
    )
    expect(screen.getByText(/Sent for review by Pia Preparer/)).toBeInTheDocument()
    expect(screen.getByText(/Approved by Rex Reviewer/)).toBeInTheDocument()
    expect(screen.getByText('Looks good.')).toBeInTheDocument()
  })

  // ---- submit ---------------------------------------------------------------------------------------------

  it('submits after a confirmation, and locks the screen to the answer from the server', async () => {
    const inReview = withActions({}, { lifecycle: 'IN_REVIEW', editable: false })
    const fake = show(withActions({ canSubmit: true }), { warningCount: 2, handlers: { 'POST /api/cases/c-1/submit-review': () => ({ body: inReview }) } })
    await userEvent.click(screen.getByRole('button', { name: 'Submit for review' }))

    const dialog = await screen.findByRole('dialog', { name: 'Submit for review?' })
    expect(dialog).toHaveTextContent('2 warnings')
    expect(dialog).toHaveTextContent('locked')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit anyway' }))

    await waitFor(() => expect(posts(fake, 'submit-review')).toEqual([{ acknowledgeWarnings: true }]))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('cannot submit while the checklist has errors', () => {
    show(withActions({ canSubmit: true }), { hasErrors: true })
    expect(screen.getByRole('button', { name: 'Submit for review' })).toBeDisabled()
    expect(screen.getByText(/Fix the errors above/)).toBeInTheDocument()
  })

  it("shows the server's reason when a step is refused", async () => {
    show(withActions({ canSubmit: true }), {
      handlers: { 'POST /api/cases/c-1/submit-review': () => ({ status: 409, body: { code: 'CONFLICT', message: 'The case cannot be submitted yet: Candidate full name is required.' } }) },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Submit for review' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }))
    expect(await screen.findByText(/cannot be submitted yet/)).toBeInTheDocument()
  })

  // ---- approve / request changes ---------------------------------------------------------------------------

  it('approves with an optional comment', async () => {
    const fake = show(withActions({ canApprove: true, canRequestChanges: true }, { lifecycle: 'IN_REVIEW' }), {
      handlers: { 'POST /api/cases/c-1/approve': () => ({ body: withActions({}, { lifecycle: 'APPROVED' }) }) },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Approve' }))
    const dialog = await screen.findByRole('dialog', { name: 'Approve this case?' })
    await userEvent.type(within(dialog).getByLabelText('Comment (optional)'), '  All checked.  ')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Approve' }))
    await waitFor(() => expect(posts(fake, 'approve')).toEqual([{ comment: 'All checked.' }]))
  })

  it('needs a reason to send a case back', async () => {
    const fake = show(withActions({ canApprove: true, canRequestChanges: true }, { lifecycle: 'IN_REVIEW' }), {
      handlers: { 'POST /api/cases/c-1/request-changes': () => ({ body: withActions({}, { lifecycle: 'CHANGES_REQUESTED' }) }) },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Request changes' }))
    const dialog = await screen.findByRole('dialog', { name: 'Send back for changes' })
    const send = within(dialog).getByRole('button', { name: 'Send back' })
    expect(send).toBeDisabled()
    await userEvent.type(within(dialog).getByLabelText('What needs to change?'), 'The employee ID looks wrong.')
    expect(send).toBeEnabled()
    await userEvent.click(send)
    await waitFor(() => expect(posts(fake, 'request-changes')).toEqual([{ comment: 'The employee ID looks wrong.' }]))
  })

  it('shows a refusal such as the maker-checker rule inside the dialog', async () => {
    show(withActions({ canApprove: true }, { lifecycle: 'IN_REVIEW' }), {
      handlers: { 'POST /api/cases/c-1/approve': () => ({ status: 403, body: { code: 'FORBIDDEN', message: 'You prepared or submitted this case, so you cannot approve it.' } }) },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Approve' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Approve' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to do that.')
  })

  // ---- finalize --------------------------------------------------------------------------------------------------

  const approved = () =>
    withActions({ canFinalize: true }, {
      lifecycle: 'APPROVED',
      workflow: { ...caseFixture().workflow, approvedAt: '2026-09-24T09:00:00Z', actions: { ...NO_ACTIONS, canFinalize: true } },
    })

  it('finalizes a draft made after the approval, with an optional password', async () => {
    const fake = show(approved(), {
      versions: [draftVersion(2, '2026-09-24T10:00:00Z'), draftVersion(1, '2026-09-24T08:00:00Z')],
      handlers: { 'POST /api/cases/c-1/reports/2/finalize': () => ({ body: { ...draftVersion(3, '2026-09-24T11:00:00Z'), kind: 'FINAL', encrypted: true } }) },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Finalize report' }))
    const dialog = await screen.findByRole('dialog', { name: 'Finalize the report' })

    const choices = await within(dialog).findByLabelText('Draft to finalize')
    expect(within(choices).getAllByRole('option')).toHaveLength(1) // version 1 was made before the approval
    expect(choices).toHaveDisplayValue(/Version 2/)

    await userEvent.type(within(dialog).getByLabelText(/Password to open the file/), 'open-sesame-2026')
    await userEvent.type(await within(dialog).findByLabelText('Repeat the password'), 'open-sesame-2026')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Finalize' }))
    await waitFor(() => expect(posts(fake, 'reports/2/finalize')).toEqual([{ openPassword: 'open-sesame-2026' }]))
  })

  it('sends no password when none is typed', async () => {
    const fake = show(approved(), {
      versions: [draftVersion(2, '2026-09-24T10:00:00Z')],
      handlers: { 'POST /api/cases/c-1/reports/2/finalize': () => ({ body: draftVersion(3, '2026-09-24T11:00:00Z') }) },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Finalize report' }))
    const dialog = await screen.findByRole('dialog', { name: 'Finalize the report' })
    await within(dialog).findByLabelText('Draft to finalize')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Finalize' }))
    await waitFor(() => expect(posts(fake, 'reports/2/finalize')).toEqual([{ openPassword: null }]))
  })

  it('checks the password before sending', async () => {
    const fake = show(approved(), { versions: [draftVersion(2, '2026-09-24T10:00:00Z')] })
    await userEvent.click(screen.getByRole('button', { name: 'Finalize report' }))
    const dialog = await screen.findByRole('dialog', { name: 'Finalize the report' })
    await within(dialog).findByLabelText('Draft to finalize')
    await userEvent.type(within(dialog).getByLabelText(/Password to open the file/), 'short')
    await userEvent.type(await within(dialog).findByLabelText('Repeat the password'), 'short')
    expect(within(dialog).getByText('Use 8 to 128 characters, or leave the password empty.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Finalize' })).toBeDisabled()
    await userEvent.type(within(dialog).getByLabelText(/Password to open the file/), 'er-and-longer')
    expect(within(dialog).getByText('The two passwords are not the same.')).toBeInTheDocument()
    expect(posts(fake, 'reports/2/finalize')).toHaveLength(0)
  })

  it('asks for a fresh draft when none was made after the approval', async () => {
    show(approved(), { versions: [draftVersion(1, '2026-09-24T08:00:00Z')] })
    await userEvent.click(screen.getByRole('button', { name: 'Finalize report' }))
    const dialog = await screen.findByRole('dialog', { name: 'Finalize the report' })
    expect(await within(dialog).findByText(/no draft made after the approval/i)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Finalize' })).toBeDisabled()
  })

  // ---- reopen and history ----------------------------------------------------------------------------------

  it('reopens a finalized case with a reason', async () => {
    const fake = show(withActions({ canReopen: true }, { lifecycle: 'FINALIZED', editable: false }), {
      handlers: { 'POST /api/cases/c-1/reopen': () => ({ body: withActions({}, { lifecycle: 'DRAFT' }) }) },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Reopen for changes' }))
    const dialog = await screen.findByRole('dialog', { name: 'Reopen this case?' })
    expect(within(dialog).getByRole('button', { name: 'Reopen' })).toBeDisabled()
    await userEvent.type(within(dialog).getByLabelText('Why is it reopened?'), 'Client asked for a correction')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reopen' }))
    await waitFor(() => expect(posts(fake, 'reopen')).toEqual([{ reason: 'Client asked for a correction' }]))
  })

  it('lists the history in plain words', async () => {
    show(withActions({}, { lifecycle: 'APPROVED' }), {
      history: [
        { action: 'SUBMIT', from: 'DRAFT', to: 'IN_REVIEW', actorId: 'a', actorName: 'Pia Preparer', comment: null, reportVersion: null, at: '2026-09-24T08:00:00Z' },
        { action: 'REQUEST_CHANGES', from: 'IN_REVIEW', to: 'CHANGES_REQUESTED', actorId: 'b', actorName: 'Rex Reviewer', comment: 'Fix the ID', reportVersion: null, at: '2026-09-24T09:00:00Z' },
        { action: 'FINALIZE', from: 'APPROVED', to: 'FINALIZED', actorId: 'b', actorName: 'Rex Reviewer', comment: null, reportVersion: 3, at: '2026-09-24T10:00:00Z' },
      ],
    })
    const list = await screen.findByRole('list', { name: 'Case history' })
    const items = within(list).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Pia Preparer sent the case for review')
    expect(items[1]).toHaveTextContent('Rex Reviewer sent the case back for changes: "Fix the ID"')
    expect(items[2]).toHaveTextContent('Rex Reviewer finalized the report (report v3)')
  })

  it('gives the History toggle room to click, and writes history times day first', async () => {
    const at = new Date(2026, 8, 24, 8, 0).toISOString() // local parts: the same answer in every time zone
    show(withActions({}, { lifecycle: 'APPROVED' }), {
      history: [{ action: 'SUBMIT', from: 'DRAFT', to: 'IN_REVIEW', actorId: 'a', actorName: 'Pia Preparer', comment: null, reportVersion: null, at }],
    })
    const list = await screen.findByRole('list', { name: 'Case history' })
    expect(within(list).getByText('24/09/2026, 08:00')).toBeInTheDocument()
    // jsdom has no layout: py-1 makes the toggle at least 24 px tall
    expect(screen.getByText(/^History \(1\)$/)).toHaveClass('py-1')
  })
})
