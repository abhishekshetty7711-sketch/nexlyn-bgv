import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes } from '@/test/testUtils'
import { buildCaseQuery, EMPTY_CASE_FILTERS } from './api'
import { CasesPage } from './CasesPage'
import { caseFixture } from './testFixtures'
import type { CaseRow } from './types'

const CLIENTS = [
  { id: 'cl-1', name: 'Acme Corp', displayName: 'Acme Corp', defaultCheckTypes: [], active: true, version: 0 },
  { id: 'cl-2', name: 'Beta Ltd', displayName: 'Beta Ltd', defaultCheckTypes: [], active: true, version: 0 },
]

function row(overrides: Partial<CaseRow> = {}): CaseRow {
  return {
    id: 'c-1',
    reportId: 'NX-2026-0001',
    clientName: 'Acme Corp',
    candidateName: 'Asha Rao',
    employeeId: 'EMP-1',
    lifecycle: 'DRAFT',
    issueDate: '2026-09-24',
    dueDate: null,
    assignments: [{ adminId: 'a-1', fullName: 'Ann Analyst', email: null, role: 'PREPARER', assignedAt: 'x' }],
    savedSections: 2,
    updatedAt: '2026-09-24T10:00:00Z',
    ...overrides,
  }
}

const page = (items: CaseRow[], total = items.length) => ({ body: { items, page: 0, size: 25, total } })

function show(permissions = ['CASE_READ_ASSIGNED']) {
  return renderRoutes(
    [
      { path: '/cases', element: <CasesPage /> },
      { path: '/cases/:id', element: <p>workspace opened</p> },
    ],
    { route: '/cases', auth: fakeAuth({ permissions }) },
  )
}

describe('buildCaseQuery', () => {
  it('always pages and leaves out empty filters', () => {
    expect(buildCaseQuery(EMPTY_CASE_FILTERS, 0)).toBe('page=0&size=25')
  })

  it('sends the filters that were set, trimmed', () => {
    const query = new URLSearchParams(buildCaseQuery({ status: 'IN_REVIEW', client: 'cl-1', q: '  asha ' }, 2))
    expect(query.get('status')).toBe('IN_REVIEW')
    expect(query.get('client')).toBe('cl-1')
    expect(query.get('q')).toBe('asha')
    expect(query.get('page')).toBe('2')
  })
})

describe('CasesPage', () => {
  it('lists the cases with candidate, client, status and who is assigned', async () => {
    mockFetch({ 'GET /api/cases': () => page([row(), row({ id: 'c-2', reportId: 'NX-2026-0002', candidateName: null, employeeId: null, lifecycle: 'IN_REVIEW' })]), 'GET /api/clients': () => ({ body: CLIENTS }) })
    show()

    const first = (await screen.findByRole('link', { name: 'NX-2026-0001' })).closest('tr')!
    expect(within(first).getByText('Asha Rao')).toBeInTheDocument()
    expect(within(first).getByText('EMP-1')).toBeInTheDocument()
    expect(within(first).getByText('Draft')).toBeInTheDocument()
    expect(within(first).getByText('Ann Analyst')).toBeInTheDocument()
    expect(within(first).getByText('24/09/2026')).toBeInTheDocument()
    const second = screen.getByRole('link', { name: 'NX-2026-0002' }).closest('tr')!
    expect(within(second).getByText('Not entered yet')).toBeInTheDocument()
    expect(within(second).getByText('In review')).toBeInTheDocument()
    expect(screen.getByText('2 cases')).toBeInTheDocument()
  })

  it('opens a case when its Report ID is clicked', async () => {
    mockFetch({ 'GET /api/cases': () => page([row()]), 'GET /api/clients': () => ({ body: CLIENTS }) })
    show()
    await userEvent.click(await screen.findByRole('link', { name: 'NX-2026-0001' }))
    expect(await screen.findByText('workspace opened')).toBeInTheDocument()
  })

  it('says so when nothing matches', async () => {
    mockFetch({ 'GET /api/cases': () => page([]), 'GET /api/clients': () => ({ body: CLIENTS }) })
    show()
    expect(await screen.findByText('No cases match.')).toBeInTheDocument()
  })

  it('applies the search, status and client filters and returns to the first page', async () => {
    const fake = mockFetch({ 'GET /api/cases': () => page([row()]), 'GET /api/clients': () => ({ body: CLIENTS }) })
    show()
    await screen.findByRole('link', { name: 'NX-2026-0001' })

    await userEvent.type(screen.getByLabelText('Search'), 'asha')
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'IN_REVIEW')
    await userEvent.selectOptions(screen.getByLabelText('Client'), 'cl-2')
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }))

    await screen.findByRole('link', { name: 'NX-2026-0001' })
    const last = String(fake.mock.calls.filter(([url]) => String(url).startsWith('/api/cases?')).at(-1)![0])
    const query = new URLSearchParams(last.split('?')[1])
    expect(query.get('q')).toBe('asha')
    expect(query.get('status')).toBe('IN_REVIEW')
    expect(query.get('client')).toBe('cl-2')
    expect(query.get('page')).toBe('0')
  })

  it('shows only the New case button to those who may create cases', async () => {
    mockFetch({ 'GET /api/cases': () => page([row()]), 'GET /api/clients': () => ({ body: CLIENTS }) })
    const { unmount } = show(['CASE_READ_ASSIGNED'])
    await screen.findByRole('link', { name: 'NX-2026-0001' })
    expect(screen.queryByRole('button', { name: 'New case' })).not.toBeInTheDocument()
    unmount()

    mockFetch({ 'GET /api/cases': () => page([row()]), 'GET /api/clients': () => ({ body: CLIENTS }) })
    show(['CASE_READ_ASSIGNED', 'CASE_CREATE'])
    expect(await screen.findByRole('button', { name: 'New case' })).toBeInTheDocument()
  })

  it('creates a case for a chosen client and opens its workspace', async () => {
    const fake = mockFetch({
      'GET /api/cases': () => page([]),
      'GET /api/clients': () => ({ body: CLIENTS }),
      'POST /api/cases': () => ({ status: 201, body: caseFixture({ id: 'c-9' }) }),
    })
    show(['CASE_READ_ASSIGNED', 'CASE_CREATE'])

    await userEvent.click(await screen.findByRole('button', { name: 'New case' }))
    const dialog = screen.getByRole('dialog', { name: 'New case' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create case' }))
    expect(await within(dialog).findByText('Choose a client')).toBeInTheDocument()
    expect(fake.mock.calls.some(([url, init]) => url === '/api/cases' && init?.method === 'POST')).toBe(false)

    await userEvent.selectOptions(within(dialog).getByLabelText('Client'), 'cl-1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create case' }))

    expect(await screen.findByText('workspace opened')).toBeInTheDocument()
    const post = fake.mock.calls.find(([url, init]) => url === '/api/cases' && init?.method === 'POST')!
    const body = JSON.parse(post[1]!.body as string) as Record<string, unknown>
    expect(body.clientId).toBe('cl-1')
    expect(body.issueDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(body).not.toHaveProperty('dueDate')
  })

  it('warns when there is no client to choose yet', async () => {
    mockFetch({ 'GET /api/cases': () => page([]), 'GET /api/clients': () => ({ body: [] }) })
    show(['CASE_READ_ASSIGNED', 'CASE_CREATE'])
    await userEvent.click(await screen.findByRole('button', { name: 'New case' }))
    expect(await screen.findByText('There are no active clients yet. Add a client first.')).toBeInTheDocument()
  })

  it('shows why creation failed', async () => {
    mockFetch({
      'GET /api/cases': () => page([]),
      'GET /api/clients': () => ({ body: CLIENTS }),
      'POST /api/cases': () => ({ status: 400, body: { code: 'VALIDATION_FAILED', message: 'Choose an active client.' } }),
    })
    show(['CASE_READ_ASSIGNED', 'CASE_CREATE'])
    await userEvent.click(await screen.findByRole('button', { name: 'New case' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.selectOptions(await within(dialog).findByLabelText('Client'), 'cl-1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create case' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Choose an active client.')
  })

  it('reports a failure to load', async () => {
    mockFetch({ 'GET /api/cases': () => ({ status: 403, body: { code: 'FORBIDDEN', message: 'no' } }), 'GET /api/clients': () => ({ body: CLIENTS }) })
    show()
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to do that.')
  })
})
