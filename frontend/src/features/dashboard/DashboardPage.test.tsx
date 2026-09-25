import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes } from '@/test/testUtils'
import type { CaseRow } from '../cases/types'
import type { Dashboard } from './api'
import { DashboardPage } from './DashboardPage'

const row = (overrides: Partial<CaseRow> = {}): CaseRow => ({
  id: 'c-1',
  reportId: 'NX-2026-0001',
  clientName: 'Acme Corp',
  candidateName: 'Asha Rao',
  employeeId: 'E-1',
  lifecycle: 'DRAFT',
  issueDate: '2026-09-24',
  dueDate: null,
  assignments: [],
  savedSections: 2,
  updatedAt: '2026-09-24T10:00:00Z',
  ...overrides,
})

const dashboard = (overrides: Partial<Dashboard> = {}): Dashboard => ({
  counts: { DRAFT: 4, IN_REVIEW: 2, CHANGES_REQUESTED: 1, APPROVED: 0, FINALIZED: 7 },
  mine: [],
  awaitingMyReview: [],
  dueSoon: [],
  overdue: 0,
  today: '2026-09-25',
  ...overrides,
})

function show(data: Dashboard | { status: number }, permissions = ['CASE_READ_ALL', 'REPORT_APPROVE']) {
  mockFetch({
    'GET /api/dashboard': () => ('status' in data ? { status: data.status, body: { code: 'X', message: 'Something failed.' } } : { body: data }),
    'GET /actuator/health': () => ({ body: { status: 'UP' } }),
  })
  return renderRoutes([{ path: '/', element: <DashboardPage /> }], { auth: fakeAuth({ permissions }) })
}

describe('DashboardPage', () => {
  it('shows how many cases are in each stage, each linking to the filtered list', async () => {
    show(dashboard())
    const stages = await screen.findByRole('region', { name: 'Cases by stage' })
    const draft = within(stages).getByRole('link', { name: 'Draft: 4 cases' })
    expect(draft).toHaveAttribute('href', '/cases?status=DRAFT')
    expect(within(stages).getByRole('link', { name: 'Finalized: 7 cases' })).toHaveAttribute('href', '/cases?status=FINALIZED')
    expect(within(stages).getByRole('link', { name: 'In review: 2 cases' })).toBeInTheDocument()
    expect(within(stages).getByRole('link', { name: 'Approved: 0 cases' })).toBeInTheDocument()
  })

  it('lists my cases, what waits for my review and what is due, with links to open them', async () => {
    show(
      dashboard({
        mine: [row()],
        awaitingMyReview: [row({ id: 'c-2', reportId: 'NX-2026-0002', lifecycle: 'IN_REVIEW', candidateName: 'Ben Rao' })],
        dueSoon: [row({ id: 'c-3', reportId: 'NX-2026-0003', dueDate: '2026-09-26' })],
      }),
    )
    const review = await screen.findByRole('region', { name: 'Waiting for my review' })
    expect(within(review).getByRole('link', { name: 'NX-2026-0002' })).toHaveAttribute('href', '/cases/c-2')
    expect(review).toHaveTextContent('Ben Rao · Acme Corp')
    expect(review).toHaveTextContent('In review')
    expect(within(screen.getByRole('region', { name: 'My cases' })).getByRole('link', { name: 'NX-2026-0001' })).toBeInTheDocument()
    const due = screen.getByRole('region', { name: 'Due soon and overdue' })
    expect(due).toHaveTextContent('due 26/09/2026')
  })

  it('marks a due date that has passed as overdue, and one still ahead not', async () => {
    show(
      dashboard({
        dueSoon: [row({ id: 'c-old', reportId: 'NX-2020-0001', dueDate: '2020-01-02' }), row({ id: 'c-far', reportId: 'NX-2099-0001', dueDate: '2099-01-02' })],
      }),
    )
    const due = await screen.findByRole('region', { name: 'Due soon and overdue' })
    const items = within(due).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('due 02/01/2020 (overdue)')
    expect(items[1]).toHaveTextContent('due 02/01/2099')
    expect(items[1]).not.toHaveTextContent('overdue')
  })

  it('shows how many cases each list holds, and a placeholder while loading', async () => {
    show(dashboard({ mine: [row(), row({ id: 'c-2', reportId: 'NX-2026-0002' })] }))
    expect(screen.getAllByRole('status', { name: 'Loading' }).length).toBeGreaterThan(0)
    const mine = await screen.findByRole('region', { name: 'My cases' })
    expect(within(mine).getByText('2', { selector: 'span' })).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Loading' })).not.toBeInTheDocument()
  })

  it('says so when there is nothing to do', async () => {
    show(dashboard())
    expect(await screen.findByText('Nothing is waiting for your review.')).toBeInTheDocument()
    expect(screen.getByText('No open cases are assigned to you.')).toBeInTheDocument()
    expect(screen.getByText('No case is due in the next 3 days.')).toBeInTheDocument()
    expect(screen.queryByText(/overdue\./)).not.toBeInTheDocument()
  })

  it('calls out overdue cases', async () => {
    show(dashboard({ overdue: 3 }))
    expect(await screen.findByText(/3 cases are overdue/)).toBeInTheDocument()
  })

  it('does not offer the review list to someone who cannot approve', async () => {
    show(dashboard(), ['CASE_READ_ASSIGNED'])
    await screen.findByRole('region', { name: 'My cases' })
    expect(screen.queryByRole('region', { name: 'Waiting for my review' })).not.toBeInTheDocument()
  })

  it('does not ask for case data an account has no access to', async () => {
    const fake = show(dashboard(), ['AUDIT_READ'])
    expect(await screen.findByText(/does not include access to cases/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Cases by stage' })).not.toBeInTheDocument()
    void fake
  })

  it('shows the backend status and a failure to load', async () => {
    show({ status: 500 })
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(await screen.findByText('Backend status: UP')).toBeInTheDocument()
  })
})
