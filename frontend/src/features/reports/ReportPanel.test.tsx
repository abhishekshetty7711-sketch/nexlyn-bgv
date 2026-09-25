import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes, type FakeHandler } from '@/test/testUtils'
import type { ReportJob, ReportVersion } from './api'
import { ReportPanel } from './ReportPanel'

const VERSION: ReportVersion = {
  version: 2,
  kind: 'DRAFT',
  sizeBytes: 350_000,
  pageCount: 5,
  encrypted: false,
  generatedByName: 'Ann Analyst',
  generatedAt: '2026-09-24T10:30:00Z',
  finalizedAt: null,
  warnings: [],
}
const FINAL: ReportVersion = { ...VERSION, version: 3, kind: 'FINAL', encrypted: true, finalizedAt: '2026-09-25T09:00:00Z' }

const jobFixture = (overrides: Partial<ReportJob> = {}): ReportJob => ({
  id: 'j-1',
  caseId: 'c-1',
  status: 'DONE',
  version: 3,
  error: null,
  warnings: [],
  requestedAt: '2026-09-24T10:30:00Z',
  finishedAt: '2026-09-24T10:30:05Z',
  ...overrides,
})

function serve(versions: ReportVersion[], extra: Record<string, FakeHandler> = {}) {
  return mockFetch({ 'GET /api/cases/c-1/reports': () => ({ body: versions }), ...extra })
}

function show(options: { hasErrors?: boolean; warningCount?: number; permissions?: string[]; finalized?: boolean } = {}) {
  return renderRoutes(
    [
      {
        path: '/',
        element: <ReportPanel caseId="c-1" reportId="NX-2026-0142" hasErrors={options.hasErrors ?? false} warningCount={options.warningCount ?? 0} finalized={options.finalized ?? false} />,
      },
    ],
    { auth: fakeAuth({ permissions: options.permissions ?? ['REPORT_GENERATE'] }) },
  )
}

const posts = (fake: ReturnType<typeof mockFetch>) =>
  fake.mock.calls.filter(([url, init]) => url === '/api/cases/c-1/reports' && init?.method === 'POST').map(([, init]) => JSON.parse(init!.body as string) as unknown)

afterEach(() => vi.restoreAllMocks())

describe('ReportPanel', () => {
  it('says when no report has been made yet', async () => {
    serve([])
    show()
    expect(await screen.findByText('No report has been generated yet.')).toBeInTheDocument()
  })

  it('does not invite a new draft on a finalized case: the button is off and the reason is written next to it', async () => {
    const fake = serve([FINAL])
    show({ finalized: true, permissions: ['REPORT_GENERATE', 'REPORT_DOWNLOAD_FINAL'] })
    await screen.findByRole('table', { name: 'Report versions' })

    const button = screen.getByRole('button', { name: 'Generate draft PDF' })
    expect(button).toBeDisabled()
    expect(button).toHaveAccessibleDescription(/Reopen the case to make a new draft/)
    expect(screen.getByText(/This case is finalized/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Preview' })).toBeEnabled() // looking is still allowed
    expect(screen.getByRole('button', { name: 'Download version 3' })).toBeInTheDocument()
    expect(posts(fake)).toEqual([])
  })

  it('does not explain a finalized case to someone who cannot generate anyway', async () => {
    serve([FINAL])
    show({ finalized: true, permissions: [] })
    await screen.findByRole('table', { name: 'Report versions' })
    expect(screen.queryByRole('button', { name: 'Generate draft PDF' })).not.toBeInTheDocument()
    expect(screen.queryByText(/This case is finalized/)).not.toBeInTheDocument()
  })

  it('gives the versions table a header for its action column', async () => {
    serve([VERSION])
    show()
    const table = await screen.findByRole('table', { name: 'Report versions' })
    expect(within(table).getAllByRole('columnheader').every((header) => header.textContent?.trim())).toBe(true)
    expect(within(table).getByRole('columnheader', { name: 'Actions' })).toBeInTheDocument()
  })

  it('lists the versions, newest first, with who made them', async () => {
    serve([FINAL, VERSION])
    show()
    const table = await screen.findByRole('table', { name: 'Report versions' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('v3')
    expect(rows[0]).toHaveTextContent('Final')
    expect(rows[0]).toHaveTextContent('password protected')
    expect(rows[1]).toHaveTextContent('v2')
    expect(rows[1]).toHaveTextContent('Draft')
    expect(rows[1]).toHaveTextContent('Ann Analyst')
    expect(rows[1]).toHaveTextContent('342 KB')
  })

  // ---- preview ------------------------------------------------------------------------------------------

  it('shows the preview in a sandboxed frame, without scripts', async () => {
    serve([], { 'GET /api/cases/c-1/reports/preview': () => ({ body: undefined }) })
    // the preview is HTML text, not JSON: answer it with a plain Response
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/reports/preview')
          ? new Response('<html><body><p>Preview of NX-2026-0142</p></body></html>', { headers: { 'Content-Type': 'text/html' } })
          : new Response('[]', { headers: { 'Content-Type': 'application/json' } }),
      ),
    )
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Preview' }))

    const dialog = await screen.findByRole('dialog', { name: 'Report preview' })
    const frame = within(dialog).getByTitle('Report preview')
    expect(frame).toHaveAttribute('srcdoc', expect.stringContaining('Preview of NX-2026-0142'))
    expect(frame.getAttribute('sandbox')).toBe('')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('works for a preview even while errors block the PDF', async () => {
    serve([])
    show({ hasErrors: true })
    expect(await screen.findByRole('button', { name: 'Preview' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Generate draft PDF' })).toBeDisabled()
    expect(screen.getByText(/Fix the errors above/)).toBeInTheDocument()
  })

  // ---- generating ---------------------------------------------------------------------------------------

  it('generates a draft, follows the job and shows the new version', async () => {
    let versions: ReportVersion[] = []
    const fake = serve([], {
      'GET /api/cases/c-1/reports': () => ({ body: versions }),
      'POST /api/cases/c-1/reports': () => ({ status: 202, body: jobFixture({ status: 'QUEUED', version: null, finishedAt: null }) }),
      'GET /api/cases/c-1/reports/jobs/j-1': () => {
        versions = [{ ...VERSION, version: 3 }]
        return { body: jobFixture() }
      },
    })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Generate draft PDF' }))

    expect(await screen.findByText('Report version 3 is ready below.')).toBeInTheDocument()
    expect(posts(fake)).toEqual([{ acknowledgeWarnings: false }])
    expect(await screen.findByRole('table', { name: 'Report versions' })).toHaveTextContent('v3')
  })

  it('shows the pages that did not fit when the report is ready', async () => {
    serve([], {
      'POST /api/cases/c-1/reports': () => ({ status: 202, body: jobFixture() }),
      'GET /api/cases/c-1/reports/jobs/j-1': () => ({ body: jobFixture({ warnings: ['Page 4: the content does not fit on the page and is cut off.'] }) }),
    })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Generate draft PDF' }))
    expect(await screen.findByText(/Some pages need attention/)).toBeInTheDocument()
    expect(screen.getByText(/Page 4: the content does not fit/)).toBeInTheDocument()
  })

  it('shows why a report failed', async () => {
    serve([], {
      'POST /api/cases/c-1/reports': () => ({ status: 202, body: jobFixture({ status: 'QUEUED' }) }),
      'GET /api/cases/c-1/reports/jobs/j-1': () => ({ body: jobFixture({ status: 'FAILED', version: null, error: 'The PDF could not be made right now. Please try again in a moment.' }) }),
    })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Generate draft PDF' }))
    expect(await screen.findByText('The PDF could not be made right now. Please try again in a moment.')).toBeInTheDocument()
  })

  it("shows the server's refusal, for example when a report is already being made", async () => {
    serve([], { 'POST /api/cases/c-1/reports': () => ({ status: 409, body: { code: 'CONFLICT', message: 'A report for this case is already being made. Wait for it to finish.' } }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Generate draft PDF' }))
    expect(await screen.findByText(/already being made/)).toBeInTheDocument()
  })

  it('asks for confirmation when there are warnings, and then sends the acknowledgement', async () => {
    const fake = serve([], {
      'POST /api/cases/c-1/reports': () => ({ status: 202, body: jobFixture() }),
      'GET /api/cases/c-1/reports/jobs/j-1': () => ({ body: jobFixture() }),
    })
    show({ warningCount: 3 })
    await userEvent.click(await screen.findByRole('button', { name: 'Generate draft PDF' }))

    const dialog = await screen.findByRole('dialog', { name: 'Generate with warnings?' })
    expect(dialog).toHaveTextContent('3 warnings')
    expect(posts(fake)).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Go back' }))
    expect(posts(fake)).toHaveLength(0)

    await userEvent.click(screen.getByRole('button', { name: 'Generate draft PDF' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Generate anyway' }))
    await waitFor(() => expect(posts(fake)).toEqual([{ acknowledgeWarnings: true }]))
  })

  // ---- who sees what -----------------------------------------------------------------------------------------------

  it('has no generate button without the permission', async () => {
    serve([VERSION])
    show({ permissions: [] })
    await screen.findByRole('table', { name: 'Report versions' })
    expect(screen.queryByRole('button', { name: 'Generate draft PDF' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Preview' })).toBeInTheDocument()
  })

  it('offers a final report for download only to people who may', async () => {
    serve([FINAL, VERSION])
    show({ permissions: [] })
    await screen.findByRole('table', { name: 'Report versions' })
    expect(screen.getByRole('button', { name: 'Download version 2' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Download version 3' })).not.toBeInTheDocument()
  })

  it('shows the final report download to someone with the permission', async () => {
    serve([FINAL])
    show({ permissions: ['REPORT_DOWNLOAD_FINAL'] })
    expect(await screen.findByRole('button', { name: 'Download version 3' })).toBeInTheDocument()
  })

  // ---- download -----------------------------------------------------------------------------------------------------

  it('saves a version as a file named after the report ID', async () => {
    serve([VERSION], { 'GET /api/cases/c-1/reports/2/download': () => ({ body: { pretend: 'pdf bytes' } }) })
    const saved: { name: string; href: string }[] = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      saved.push({ name: this.download, href: this.href })
    })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Download version 2' }))
    await waitFor(() => expect(saved).toHaveLength(1))
    expect(saved[0]!.name).toBe('NX-2026-0142_v2.pdf')
    expect(saved[0]!.href).toMatch(/^blob:/)
  })

  it('shows why a download failed', async () => {
    serve([VERSION], { 'GET /api/cases/c-1/reports/2/download': () => ({ status: 503, body: { code: 'SERVICE_UNAVAILABLE', message: 'The stored report did not pass its integrity check, so it was not sent.' } }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Download version 2' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/server had a problem/i)
  })
})
