import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes } from '@/test/testUtils'
import { buildAuditQuery, EMPTY_FILTERS, toInstant } from './api'
import { AuditPage } from './AuditPage'

const ENTRY = {
  id: 'e-1',
  at: '2026-09-24T10:00:00Z',
  actorId: 'a-1',
  actorEmail: 'owner@example.com',
  action: 'ADMIN_DISABLED',
  entityType: 'ADMIN',
  entityId: 'a-2',
  caseId: null,
  ip: '10.0.0.1',
  before: { status: 'ACTIVE' },
  after: { status: 'DISABLED' },
  correlationId: null,
}

describe('buildAuditQuery', () => {
  it('always pages, and leaves out empty filters', () => {
    expect(buildAuditQuery(EMPTY_FILTERS, 0)).toBe('page=0&size=50')
  })

  it('sends trimmed text filters and converts dates to ISO instants', () => {
    const query = new URLSearchParams(
      buildAuditQuery({ actor: ' owner@example.com ', action: 'ADMIN_DISABLED', entity: 'admin', from: '2026-09-24T10:00', to: '' }, 2),
    )
    expect(query.get('page')).toBe('2')
    expect(query.get('actor')).toBe('owner@example.com')
    expect(query.get('action')).toBe('ADMIN_DISABLED')
    expect(query.get('entity')).toBe('admin')
    expect(query.get('from')).toBe(new Date('2026-09-24T10:00').toISOString())
    expect(query.has('to')).toBe(false)
  })

  it('ignores a date it cannot read', () => {
    expect(toInstant('not a date')).toBeNull()
    expect(toInstant('')).toBeNull()
  })
})

describe('AuditPage', () => {
  it('names the tab', async () => {
    mockFetch({ 'GET /api/audit-log': () => ({ body: { items: [ENTRY], page: 0, size: 50, total: 1 } }) })
    renderRoutes([{ path: '/', element: <AuditPage /> }], { auth: fakeAuth({ permissions: ['AUDIT_READ'] }) })
    await screen.findByText('ADMIN_DISABLED')
    expect(document.title).toBe('Audit log - Nexlyn BGV')
  })

  it('writes the time of an entry day first with a 24-hour clock', async () => {
    const at = new Date(2026, 8, 25, 16, 53).toISOString() // local parts: the same answer in every time zone
    mockFetch({ 'GET /api/audit-log': () => ({ body: { items: [{ ...ENTRY, at }], page: 0, size: 50, total: 1 } }) })
    renderRoutes([{ path: '/', element: <AuditPage /> }], { auth: fakeAuth({ permissions: ['AUDIT_READ'] }) })
    expect(await screen.findByText('25/09/2026, 16:53')).toBeInTheDocument()
  })

  it('shows entries newest first with the before and after values', async () => {
    mockFetch({ 'GET /api/audit-log': () => ({ body: { items: [ENTRY], page: 0, size: 50, total: 1 } }) })
    renderRoutes([{ path: '/', element: <AuditPage /> }], { auth: fakeAuth({ permissions: ['AUDIT_READ'] }) })

    expect(await screen.findByText('ADMIN_DISABLED')).toBeInTheDocument()
    expect(screen.getByText('owner@example.com')).toBeInTheDocument()
    expect(screen.getByText('10.0.0.1')).toBeInTheDocument()
    expect(screen.getByText('1 entries, newest first')).toBeInTheDocument()
    expect(screen.getByText('Before / after')).toHaveClass('py-1') // jsdom has no layout: py-1 makes the toggle at least 24 px tall
    await userEvent.click(screen.getByText('Before / after'))
    expect(screen.getByText(/"status": "DISABLED"/)).toBeInTheDocument()
  })

  it('says so when nothing matches, and reads only (there is no way to change an entry)', async () => {
    mockFetch({ 'GET /api/audit-log': () => ({ body: { items: [], page: 0, size: 50, total: 0 } }) })
    renderRoutes([{ path: '/', element: <AuditPage /> }], { auth: fakeAuth({ permissions: ['AUDIT_READ'] }) })

    expect(await screen.findByText('No entries match.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /delete|edit|remove/i })).not.toBeInTheDocument()
  })

  it('applies the filters the admin typed and returns to the first page', async () => {
    const fake = mockFetch({ 'GET /api/audit-log': () => ({ body: { items: [ENTRY], page: 0, size: 50, total: 1 } }) })
    renderRoutes([{ path: '/', element: <AuditPage /> }], { auth: fakeAuth({ permissions: ['AUDIT_READ'] }) })
    await screen.findByText('ADMIN_DISABLED')

    await userEvent.type(screen.getByLabelText('Action'), 'LOGIN_FAILED')
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }))

    await screen.findByText('ADMIN_DISABLED')
    const urls = fake.mock.calls.map(([url]) => String(url))
    expect(urls.some((url) => url.includes('action=LOGIN_FAILED'))).toBe(true)
  })

  it('shows an error when the log cannot be read', async () => {
    mockFetch({ 'GET /api/audit-log': () => ({ status: 403, body: { code: 'FORBIDDEN', message: 'no' } }) })
    renderRoutes([{ path: '/', element: <AuditPage /> }], { auth: fakeAuth({ permissions: ['AUDIT_READ'] }) })
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to do that.')
  })
})
