import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes, type FakeHandler } from '@/test/testUtils'
import { allDefs, checkFixture, courtFixture, gapFixture } from '../checks/testFixtures'
import type { CheckView } from '../checks/types'
import { caseFixture, progressFixture, validationFixture } from '../testFixtures'
import { CaseWorkspacePage } from './CaseWorkspacePage'

const EDITOR = ['CASE_READ_ASSIGNED', 'CASE_UPDATE', 'CHECK_UPDATE']
const CLIENTS = [{ id: 'cl-1', name: 'Acme Corp', displayName: 'Acme Corp', defaultCheckTypes: ['COURT'], active: true, version: 0 }]

interface Setup {
  checks: CheckView[]
  fake: ReturnType<typeof mockFetch>
  calls: (method: string, pathPart: string) => { url: string; body: Record<string, unknown> }[]
}

function serve(checks: CheckView[], extra: Record<string, FakeHandler> = {}): Setup {
  const setup = { checks } as Setup
  setup.fake = mockFetch({
    'GET /api/cases/c-1': () => ({ body: caseFixture() }),
    'GET /api/cases/c-1/progress': () => ({ body: progressFixture() }),
    'GET /api/cases/c-1/validation': () => ({ body: validationFixture() }),
    'GET /api/cases/c-1/checks': () => ({ body: setup.checks }),
    'GET /api/check-types': () => ({ body: allDefs }),
    'GET /api/checks/ck-1/documents': () => ({ body: [] }),
    'GET /api/checks/ck-2/documents': () => ({ body: [] }),
    'GET /api/checks/ck-3/documents': () => ({ body: [] }),
    'GET /api/clients': () => ({ body: CLIENTS }),
    'GET /api/assignable-admins': () => ({ body: [] }),
    ...extra,
  })
  setup.calls = (method, pathPart) =>
    setup.fake.mock.calls
      .filter(([url, init]) => (init?.method ?? 'GET') === method && String(url).includes(pathPart))
      .map(([url, init]) => ({ url: String(url), body: typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : {} }))
  return setup
}

function open(route = '/cases/c-1?section=checks', permissions = EDITOR) {
  return renderRoutes(
    [
      { path: '/cases/:id', element: <CaseWorkspacePage /> },
      { path: '/cases', element: <p>the case list</p> },
    ],
    { route, auth: fakeAuth({ permissions }) },
  )
}

afterEach(() => vi.useRealTimers())

describe('Checks section', () => {
  it('lists the checks with their status, also under "4 Checks" in the navigator', async () => {
    serve([checkFixture(), courtFixture({ status: 'VERIFIED' })])
    open()

    const list = await screen.findByRole('list', { name: 'Checks on this case' })
    expect(within(list).getByText('Identity Verification (Aadhaar)')).toBeInTheDocument()
    expect(within(list).getByText(/Verified/)).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Case sections' })
    expect(within(nav).getByRole('list', { name: 'Checks' })).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: /^Edit / })).not.toBeInTheDocument()
  })

  it('opens a check from the address bar, drawing its form from the type definition', async () => {
    serve([checkFixture()])
    open('/cases/c-1?section=checks&check=ck-1')

    const form = await screen.findByRole('form', { name: 'Edit Identity Verification (Aadhaar)' })
    expect(within(form).getByLabelText('Title')).toHaveValue('Identity Verification (Aadhaar)')
    expect(within(form).getByLabelText('Full Name')).toHaveValue('Asha Rao')
    expect(within(form).getByLabelText('PIN Code')).toHaveValue('560001')
    expect(within(form).getByLabelText('Enter Aadhaar Number')).toHaveValue('')
    expect(within(form).getByText('★ Master')).toBeInTheDocument()
  })

  it('keeps the check\'s Save button in a sticky bar with its title, so it stays in view on the long form', async () => {
    serve([checkFixture()])
    open('/cases/c-1?section=checks&check=ck-1')
    const form = await screen.findByRole('form', { name: /^Edit / })

    // jsdom has no layout: what can be checked is that title and Save share the bar that carries the sticky rule.
    const save = within(form).getByRole('button', { name: 'Save check' })
    const bar = save.closest('.sticky')
    expect(bar).not.toBeNull()
    expect(bar).toHaveTextContent('Identity Verification (Aadhaar)')
    expect(form.contains(bar)).toBe(true)
  })

  it('names the remarks preview with a real role instead of a label on a plain div', async () => {
    serve([checkFixture()])
    open('/cases/c-1?section=checks&check=ck-1')
    await screen.findByRole('form', { name: /^Edit / })
    expect(screen.getByRole('group', { name: 'Remarks for this check preview' })).toBeInTheDocument()
  })

  it('opens a check by clicking it, and keeps the choice in the address', async () => {
    serve([checkFixture(), courtFixture()])
    const { router } = open()
    const list = await screen.findByRole('list', { name: 'Checks on this case' })
    await userEvent.click(within(list).getByRole('button', { name: 'Court Record (Permanent Address)' }))
    expect(await screen.findByRole('form', { name: /Edit Court Record/ })).toBeInTheDocument()
    expect(router.state.location.search).toContain('check=ck-2')
  })

  it('labels the related person as Guardian when the candidate has a guardian', async () => {
    serve([courtFixture()], { 'GET /api/cases/c-1': () => ({ body: caseFixture({ candidate: { ...caseFixture().candidate, parentType: 'GUARDIAN' } }) }) })
    open('/cases/c-1?section=checks&check=ck-2')
    expect(await screen.findByLabelText("Guardian's Name")).toHaveValue('Ravi Rao')
  })

  // ---- saving ----------------------------------------------------------------------------------------

  it('saves: edited prefilled fields become manual, untouched ones keep following the candidate', async () => {
    const setup = serve([checkFixture()], {
      'PUT /api/cases/c-1/checks/ck-1': () => ({ body: checkFixture({ version: 1 }) }),
    })
    open('/cases/c-1?section=checks&check=ck-1')
    const form = await screen.findByRole('form', { name: /^Edit / })

    const city = within(form).getByLabelText('City / Town')
    await userEvent.clear(city)
    await userEvent.type(city, 'Mysuru')
    await userEvent.type(within(form).getByLabelText('Enter Aadhaar Number'), '2345 6789 0124')
    await userEvent.selectOptions(within(form).getByLabelText('Status'), 'VERIFIED')
    await userEvent.click(screen.getByRole('button', { name: 'Save check' }))

    await waitFor(() => expect(setup.calls('PUT', '/checks/ck-1')).toHaveLength(1))
    const sent = setup.calls('PUT', '/checks/ck-1')[0]!.body as { version: number; status: string; fields: Record<string, unknown>[] }
    expect(sent.version).toBe(0)
    expect(sent.status).toBe('VERIFIED')
    const field = (key: string) => sent.fields.find((f) => f.key === key)
    expect(field('city')).toEqual({ key: 'city', value: 'Mysuru', manual: true, verifiedTick: false })
    expect(field('full_name')).toEqual({ key: 'full_name', manual: false, verifiedTick: false })
    expect(field('aadhaar_number')).toEqual({ key: 'aadhaar_number', value: '2345 6789 0124', verifiedTick: false })
    expect(await screen.findByText('Saved')).toBeInTheDocument()
  })

  it('does not send anything for a wrong Aadhaar number and says which field', async () => {
    const setup = serve([checkFixture()])
    open('/cases/c-1?section=checks&check=ck-1')
    const form = await screen.findByRole('form', { name: /^Edit / })

    await userEvent.type(within(form).getByLabelText('Enter Aadhaar Number'), '234567890125')
    await userEvent.type(within(form).getByLabelText('PIN Code'), '9')
    await userEvent.click(screen.getByRole('button', { name: 'Save check' }))

    expect(await within(form).findByText(/is not a valid Aadhaar number/)).toBeInTheDocument()
    expect(within(form).getByText(/must be a 6-digit PIN code/)).toBeInTheDocument()
    expect(setup.calls('PUT', '/checks/')).toHaveLength(0)
  })

  it('shows the server message and offers a reload when someone else saved first', async () => {
    serve([checkFixture()], {
      'PUT /api/cases/c-1/checks/ck-1': () => ({ status: 409, body: { code: 'CONFLICT', message: 'Someone else changed this check.' } }),
    })
    open('/cases/c-1?section=checks&check=ck-1')
    await userEvent.type(await screen.findByLabelText('Title'), '!')
    await userEvent.click(screen.getByRole('button', { name: 'Save check' }))
    expect(await screen.findByText(/Someone else changed this check/)).toBeInTheDocument()
  })

  it('edits repeatable rows and sends them as one value', async () => {
    const setup = serve([gapFixture()], { 'PUT /api/cases/c-1/checks/ck-3': () => ({ body: gapFixture({ version: 1 }) }) })
    open('/cases/c-1?section=checks&check=ck-3')
    const form = await screen.findByRole('form', { name: /^Edit / })

    await userEvent.click(within(form).getByRole('button', { name: 'Add row' }))
    await userEvent.type(within(form).getByLabelText('Gap Periods 1 Reason'), 'Career break')
    await userEvent.selectOptions(within(form).getByLabelText('Result'), 'Clear')
    await userEvent.click(screen.getByRole('button', { name: 'Save check' }))

    await waitFor(() => expect(setup.calls('PUT', '/checks/ck-3')).toHaveLength(1))
    const fields = (setup.calls('PUT', '/checks/ck-3')[0]!.body as { fields: { key: string; value?: string }[] }).fields
    expect(JSON.parse(fields.find((f) => f.key === 'gaps')!.value!)).toEqual([{ reason: 'Career break' }])
    expect(fields.find((f) => f.key === 'result')!.value).toBe('Clear')
  })

  it('adds and removes extra detail rows', async () => {
    const setup = serve([courtFixture()], { 'PUT /api/cases/c-1/checks/ck-2': () => ({ body: courtFixture({ version: 1 }) }) })
    open('/cases/c-1?section=checks&check=ck-2')
    const form = await screen.findByRole('form', { name: /^Edit / })

    await userEvent.type(within(form).getByLabelText('Detail 1 value'), 'High Court')
    await userEvent.click(within(form).getByRole('button', { name: 'Remove detail 2' }))
    await userEvent.click(within(form).getByRole('button', { name: 'Add detail' }))
    await userEvent.type(within(form).getByLabelText('Detail 2 label'), 'Case No')
    await userEvent.click(screen.getByRole('button', { name: 'Save check' }))

    await waitFor(() => expect(setup.calls('PUT', '/checks/ck-2')).toHaveLength(1))
    const sent = setup.calls('PUT', '/checks/ck-2')[0]!.body as { details: unknown[] }
    expect(sent.details).toEqual([{ label: 'Court Type', value: 'High Court' }, { label: 'Case No', value: null }])
  })

  // ---- sensitive fields ------------------------------------------------------------------------------

  const withNumber = () =>
    checkFixture({ fields: checkFixture().fields.map((f) => (f.key === 'aadhaar_number' ? { ...f, hasValue: true, value: 'XXXX XXXX 0124' } : f)) })

  it('shows only the masked number, and reveals the real one for people allowed to (then hides it)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const setup = serve([withNumber()], {
      'GET /api/cases/c-1/checks/ck-1/fields/aadhaar_number/reveal': () => ({ body: { fieldKey: 'aadhaar_number', value: '234567890124' } }),
    })
    open('/cases/c-1?section=checks&check=ck-1', [...EDITOR, 'PII_UNMASK'])

    expect(await screen.findByTestId('aadhaar_number-current')).toHaveTextContent('XXXX XXXX 0124')
    await userEvent.click(screen.getByRole('button', { name: 'Reveal Aadhaar Number' }))
    expect(await screen.findByText('234567890124')).toBeInTheDocument()
    expect(setup.calls('GET', '/reveal')).toHaveLength(1)

    act(() => {
      vi.advanceTimersByTime(30_000)
    })
    await waitFor(() => expect(screen.queryByText('234567890124')).not.toBeInTheDocument())
    expect(screen.getByTestId('aadhaar_number-current')).toHaveTextContent('XXXX XXXX 0124')
  })

  it('lets the person hide a revealed number early', async () => {
    serve([withNumber()], {
      'GET /api/cases/c-1/checks/ck-1/fields/aadhaar_number/reveal': () => ({ body: { fieldKey: 'aadhaar_number', value: '234567890124' } }),
    })
    open('/cases/c-1?section=checks&check=ck-1', [...EDITOR, 'PII_UNMASK'])
    await userEvent.click(await screen.findByRole('button', { name: 'Reveal Aadhaar Number' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Hide Aadhaar Number' }))
    expect(screen.queryByText('234567890124')).not.toBeInTheDocument()
  })

  it('has no Reveal button without the unmask permission', async () => {
    serve([withNumber()])
    open('/cases/c-1?section=checks&check=ck-1')
    expect(await screen.findByTestId('aadhaar_number-current')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Reveal/ })).not.toBeInTheDocument()
  })

  it('removes a stored number when asked', async () => {
    const setup = serve([withNumber()], { 'PUT /api/cases/c-1/checks/ck-1': () => ({ body: checkFixture({ version: 1 }) }) })
    open('/cases/c-1?section=checks&check=ck-1')
    await userEvent.click(await screen.findByLabelText('Remove the stored Aadhaar Number'))
    await userEvent.click(screen.getByRole('button', { name: 'Save check' }))
    await waitFor(() => expect(setup.calls('PUT', '/checks/ck-1')).toHaveLength(1))
    const fields = (setup.calls('PUT', '/checks/ck-1')[0]!.body as { fields: Record<string, unknown>[] }).fields
    expect(fields.find((f) => f.key === 'aadhaar_number')).toEqual({ key: 'aadhaar_number', clear: true, verifiedTick: false })
  })

  // ---- attestation and free text ---------------------------------------------------------------------

  it('lets only people with the attestation permission change the attestation', async () => {
    serve([courtFixture()])
    open('/cases/c-1?section=checks&check=ck-2')
    const box = await screen.findByLabelText(/legal attestation/)
    expect(box).toBeChecked()
    expect(box).toBeDisabled()
    expect(screen.getByLabelText('Bar Council number')).toHaveValue('KAR/670/06')
  })

  it('allows the attestation to be switched off by someone with the permission', async () => {
    serve([courtFixture()])
    open('/cases/c-1?section=checks&check=ck-2', [...EDITOR, 'ATTESTATION_APPLY'])
    const box = await screen.findByLabelText(/legal attestation/)
    expect(box).toBeEnabled()
    await userEvent.click(box)
    expect(screen.queryByLabelText('Bar Council number')).not.toBeInTheDocument()
  })

  it('adds a free text block on its own, without needing the main Save', async () => {
    const setup = serve([checkFixture()], {
      'POST /api/cases/c-1/checks/ck-1/free-sections': () => ({
        body: checkFixture({ version: 1, freeSections: [{ id: 'fs-1', kind: 'TEXT', text: 'Verified on call', documentId: null, sortOrder: 0 }] }),
      }),
    })
    open('/cases/c-1?section=checks&check=ck-1')
    await userEvent.click(await screen.findByRole('button', { name: 'Add text block' }))
    expect(screen.getByRole('button', { name: 'Add block' })).toBeDisabled()
    await userEvent.type(screen.getByLabelText('New text block'), '  Verified on call ')
    await userEvent.click(screen.getByRole('button', { name: 'Add block' }))
    await waitFor(() => expect(setup.calls('POST', '/free-sections')).toHaveLength(1))
    expect(setup.calls('POST', '/free-sections')[0]!.body).toEqual({ kind: 'TEXT', text: 'Verified on call' })
  })

  it('adds a picture block: uploads the picture for the check, then attaches it as a block', async () => {
    const setup = serve([checkFixture()], {
      'POST /api/checks/ck-1/documents': () => ({ status: 201, body: { id: 'pic-1' } }),
      'POST /api/cases/c-1/checks/ck-1/free-sections': () => ({
        body: checkFixture({ version: 1, freeSections: [{ id: 'fs-2', kind: 'IMAGE', text: null, documentId: 'pic-1', sortOrder: 0 }] }),
      }),
      'GET /api/documents/pic-1/content': () => ({ body: { pretend: 'picture' } }),
    })
    open('/cases/c-1?section=checks&check=ck-1')
    const input = await screen.findByLabelText('Choose a picture for a block')
    await userEvent.upload(input, new File([new Uint8Array([1, 2, 3])], 'chart.png', { type: 'image/png' }))

    await waitFor(() => expect(setup.calls('POST', '/free-sections')).toHaveLength(1))
    expect(setup.calls('POST', '/checks/ck-1/documents')[0]!.url).toBe('/api/checks/ck-1/documents?kind=FREE_IMAGE')
    expect(setup.calls('POST', '/free-sections')[0]!.body).toEqual({ kind: 'IMAGE', documentId: 'pic-1' })
  })

  it('shows a picture block with its picture, and can delete it', async () => {
    const withPicture = checkFixture({ freeSections: [{ id: 'fs-2', kind: 'IMAGE', text: null, documentId: 'pic-1', sortOrder: 0 }] })
    const setup = serve([withPicture], {
      'GET /api/documents/pic-1/content': () => ({ body: { pretend: 'picture' } }),
      'DELETE /api/cases/c-1/checks/ck-1/free-sections/fs-2': () => ({ body: checkFixture({ version: 1 }) }),
    })
    open('/cases/c-1?section=checks&check=ck-1')
    expect(await screen.findByRole('img', { name: 'Picture block 1' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Delete picture block 1' }))
    await waitFor(() => expect(setup.calls('DELETE', '/free-sections/fs-2')).toHaveLength(1))
  })

  it('opens the check that a document warning is about', async () => {
    const setup = serve([checkFixture()], {
      'GET /api/cases/c-1/validation': () => ({
        body: { errors: [], warnings: [{ section: 'checks', field: 'check:aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:documents', message: 'Identity: no supporting document is attached.' }] },
      }),
    })
    void setup
    const { router } = open('/cases/c-1?section=generate')
    await userEvent.click(await screen.findByRole('button', { name: 'Go to section' }))
    expect(await screen.findByRole('heading', { name: '4. Checks' })).toBeInTheDocument()
    expect(router.state.location.search).toContain('check=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
  })

  // ---- add / reorder / delete -------------------------------------------------------------------------

  it("adds a check from the picker, showing the client's usual checks first, and opens it", async () => {
    const created = courtFixture()
    const setup = serve([], {
      'POST /api/cases/c-1/checks': () => ({ status: 201, body: created }),
    })
    const { router } = open()
    await userEvent.click(await screen.findByRole('button', { name: 'Add check' }))

    const dialog = await screen.findByRole('dialog', { name: 'Add a check' })
    const headings = within(dialog).getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(headings).toEqual(['This client usually asks for', 'Other checks'])
    expect(within(within(dialog).getAllByRole('list')[0]!).getByText('Court Record (Permanent Address)')).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: /Court Record/ }))
    await waitFor(() => expect(setup.calls('POST', '/cases/c-1/checks')).toHaveLength(1))
    expect(setup.calls('POST', '/cases/c-1/checks')[0]!.body).toEqual({ type: 'COURT' })
    await waitFor(() => expect(router.state.location.search).toContain('check=ck-2'))
  })

  it('reorders with the arrow buttons', async () => {
    const setup = serve([checkFixture(), courtFixture()], { 'PATCH /api/cases/c-1/checks/order': () => ({ body: [] }) })
    open()
    await userEvent.click(await screen.findByRole('button', { name: 'Move Court Record (Permanent Address) up' }))
    await waitFor(() => expect(setup.calls('PATCH', '/checks/order')).toHaveLength(1))
    expect(setup.calls('PATCH', '/checks/order')[0]!.body).toEqual({ ids: ['ck-2', 'ck-1'] })
  })

  it('asks before removing a check, then removes it', async () => {
    const setup = serve([checkFixture()], { 'DELETE /api/cases/c-1/checks/ck-1': () => ({ status: 204 }) })
    open()
    await userEvent.click(await screen.findByRole('button', { name: 'Remove Identity Verification (Aadhaar)' }))
    const dialog = await screen.findByRole('dialog', { name: 'Remove this check?' })
    expect(setup.calls('DELETE', '/checks/')).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove check' }))
    await waitFor(() => expect(setup.calls('DELETE', '/checks/ck-1')).toHaveLength(1))
  })

  // ---- guards and read-only ---------------------------------------------------------------------------

  it('asks before switching to another check while there are unsaved changes', async () => {
    serve([checkFixture(), courtFixture()])
    open('/cases/c-1?section=checks&check=ck-1')
    await userEvent.type(await screen.findByLabelText('Title'), ' (edited)')

    const nav = screen.getByRole('navigation', { name: 'Case sections' })
    await userEvent.click(within(within(nav).getByRole('list', { name: 'Checks' })).getByRole('button', { name: /Court Record/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Unsaved changes' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Stay and keep editing' }))
    expect(screen.getByLabelText('Title')).toHaveValue('Identity Verification (Aadhaar) (edited)')

    await userEvent.click(within(within(nav).getByRole('list', { name: 'Checks' })).getByRole('button', { name: /Court Record/ }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Unsaved changes' })).getByRole('button', { name: 'Discard changes' }))
    expect(await screen.findByRole('form', { name: /Edit Court Record/ })).toBeInTheDocument()
  })

  it('is read-only without permission to change checks', async () => {
    serve([checkFixture()])
    open('/cases/c-1?section=checks&check=ck-1', ['CASE_READ_ASSIGNED', 'CASE_UPDATE'])
    expect(await screen.findByLabelText('Title')).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Save check' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add check' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Remove / })).not.toBeInTheDocument()
  })

  it('is read-only once the case is locked', async () => {
    serve([checkFixture()], { 'GET /api/cases/c-1': () => ({ body: caseFixture({ lifecycle: 'IN_REVIEW', editable: false }) }) })
    open('/cases/c-1?section=checks&check=ck-1')
    expect(await screen.findByLabelText('Title')).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Save check' })).not.toBeInTheDocument()
  })
})
