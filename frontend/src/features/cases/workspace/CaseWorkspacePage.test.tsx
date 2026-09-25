import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes, type FakeHandler } from '@/test/testUtils'
import { caseFixture, progressFixture, validationFixture } from '../testFixtures'
import type { CaseView } from '../types'
import { CaseWorkspacePage } from './CaseWorkspacePage'

const EDITOR = ['CASE_READ_ASSIGNED', 'CASE_UPDATE']
const CLIENTS = [
  { id: 'cl-1', name: 'Acme Corp', displayName: 'Acme Corp\nPrivate Limited', defaultCheckTypes: [], active: true, version: 0 },
  { id: 'cl-2', name: 'Beta Ltd', displayName: 'Beta Ltd', defaultCheckTypes: [], active: true, version: 0 },
]
const ADMINS = [
  { id: 'a-1', email: 'a@example.com', fullName: 'Ann Analyst', active: true },
  { id: 'a-2', email: 'b@example.com', fullName: 'Bob Reviewer', active: true },
]

interface Setup {
  current: CaseView
  fake: ReturnType<typeof mockFetch>
  puts: () => { url: string; body: Record<string, unknown> }[]
}

/** Serves one case; each save replaces it with the given answer (or a version bump of the sent values). */
function serve(initial: CaseView, extra: Record<string, FakeHandler> = {}): Setup {
  const setup = { current: initial } as Setup
  setup.fake = mockFetch({
    'GET /api/cases/c-1': () => ({ body: setup.current }),
    'GET /api/cases/c-1/progress': () => ({ body: progressFixture() }),
    'GET /api/cases/c-1/validation': () => ({ body: validationFixture() }),
    'GET /api/cases/c-1/checks': () => ({ body: [] }),
    'GET /api/cases/c-1/reports': () => ({ body: [] }),
    'GET /api/cases/c-1/history': () => ({ body: [] }),
    'GET /api/check-types': () => ({ body: [] }),
    'GET /api/clients': () => ({ body: CLIENTS }),
    'GET /api/assignable-admins': () => ({ body: ADMINS }),
    ...extra,
  })
  setup.puts = () =>
    setup.fake.mock.calls
      .filter(([, init]) => init?.method === 'PUT')
      .map(([url, init]) => ({ url: String(url), body: JSON.parse(init!.body as string) as Record<string, unknown> }))
  return setup
}

function open(setup: Setup, options: { permissions?: string[]; route?: string } = {}) {
  void setup
  return renderRoutes(
    [
      { path: '/cases/:id', element: <CaseWorkspacePage /> },
      { path: '/cases', element: <p>the case list</p> },
    ],
    { route: options.route ?? '/cases/c-1', auth: fakeAuth({ permissions: options.permissions ?? EDITOR }) },
  )
}

async function goTo(name: RegExp | string) {
  await userEvent.click(within(screen.getByRole('navigation', { name: 'Case sections' })).getByRole('button', { name }))
}

describe('CaseWorkspacePage', () => {
  it('shows the case header, the section list with marks, and the progress bar', async () => {
    const setup = serve(caseFixture())
    open(setup)

    expect(await screen.findByRole('heading', { name: 'NX-2026-0001' })).toBeInTheDocument()
    expect(screen.getByText('Draft')).toBeInTheDocument()
    expect(screen.getByText(/issued 24\/09\/2026/)).toBeInTheDocument()

    const nav = await screen.findByRole('navigation', { name: 'Case sections' })
    expect(within(nav).getAllByRole('button')).toHaveLength(8)
    expect(within(nav).getByRole('button', { name: /1 Report Info/ })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getAllByRole('img', { name: 'saved' })).toHaveLength(1)
    expect(within(nav).getAllByRole('img', { name: 'not started' })).toHaveLength(7)
    await waitFor(() => expect(screen.getByRole('progressbar', { name: 'Case progress' })).toHaveAttribute('aria-valuenow', '14'))
    expect(screen.getByRole('form', { name: '1. Report info' })).toBeInTheDocument()
  })

  it('has a breadcrumb back to the list that names the current case', async () => {
    const setup = serve(caseFixture())
    open(setup)

    const crumbs = await screen.findByRole('navigation', { name: 'Breadcrumb' })
    expect(within(crumbs).getByRole('link', { name: 'Cases' })).toHaveAttribute('href', '/cases')
    expect(within(crumbs).getByText('NX-2026-0001')).toHaveAttribute('aria-current', 'page')
  })

  it('starts on the section named in the address', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=settings' })
    expect(await screen.findByRole('form', { name: '7. Report settings' })).toBeInTheDocument()
  })

  it('explains when the case cannot be opened, without saying whether it exists', async () => {
    const setup = serve(caseFixture(), { 'GET /api/cases/c-1': () => ({ status: 403, body: { code: 'FORBIDDEN', message: 'no' } }) })
    open(setup)
    expect(await screen.findByRole('alert')).toHaveTextContent('does not exist, or you are not allowed to open it')
    expect(screen.getByRole('link', { name: 'Back to cases' })).toBeInTheDocument()
  })

  // ---- saving ----------------------------------------------------------------------------------------

  it('saves the candidate with the current version and shows the server\'s cleaned answer', async () => {
    const setup = serve(caseFixture({ version: 3 }), {
      'PUT /api/cases/c-1/candidate': () => {
        setup.current = caseFixture({
          version: 4,
          savedSections: { 'report-info': 'x', candidate: 'y' },
          candidate: { ...caseFixture().candidate, fullName: 'Asha Rao', employeeId: 'EMP-1', phone: '+919876543210', phoneDisplay: '+91 98765 43210' },
        })
        return { body: setup.current }
      },
    })
    open(setup)
    await screen.findByRole('heading', { name: 'NX-2026-0001' })
    await goTo(/2 Candidate/)

    await userEvent.type(await screen.findByLabelText('Full name'), '  Asha Rao ')
    await userEvent.type(screen.getByLabelText('Employee ID'), 'EMP-1')
    await userEvent.type(screen.getByLabelText('Phone'), '98765 43210')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved')).toBeInTheDocument()
    const [put] = setup.puts()
    expect(put!.url).toBe('/api/cases/c-1/candidate')
    expect(put!.body).toMatchObject({ version: 3, fullName: 'Asha Rao', employeeId: 'EMP-1', phone: '98765 43210', parentType: 'FATHER', country: 'India' })
    expect(put!.body.parentName).toBeNull()
    expect(put!.body.dob).toBeNull()
    await waitFor(() => expect(screen.getByLabelText('Phone')).toHaveValue('+91 98765 43210'))
  })

  it('checks values before sending and shows the problem next to the field', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=candidate' })

    await userEvent.type(await screen.findByLabelText('PIN code'), '56')
    await userEvent.type(screen.getByLabelText('Phone'), '12345')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Enter a 6-digit PIN code')).toBeInTheDocument()
    expect(screen.getByText(/Enter a valid Indian mobile number/)).toBeInTheDocument()
    expect(setup.puts()).toHaveLength(0)
  })

  it('switches the parent label between Father and Guardian', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=candidate' })

    expect(await screen.findByLabelText("Father's name")).toBeInTheDocument()
    await userEvent.click(screen.getByRole('radio', { name: 'Guardian' }))
    expect(screen.getByLabelText("Guardian's name")).toBeInTheDocument()
  })

  it('tells the admin someone else saved first, and reloads on request', async () => {
    const setup = serve(caseFixture({ version: 1 }), {
      'PUT /api/cases/c-1/remarks': () => ({ status: 409, body: { code: 'CONFLICT', message: 'This case was changed by someone else since you opened it. Reload it and try again.' } }),
    })
    open(setup, { route: '/cases/c-1?section=remarks' })

    await userEvent.type(await screen.findByLabelText('Analyst remarks'), 'my edit')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('changed by someone else')
    const before = setup.fake.mock.calls.filter(([url]) => url === '/api/cases/c-1').length
    await userEvent.click(screen.getByRole('button', { name: 'Reload the case' }))
    await waitFor(() => expect(setup.fake.mock.calls.filter(([url]) => url === '/api/cases/c-1').length).toBeGreaterThan(before))
  })

  // ---- unsaved changes --------------------------------------------------------------------------------------

  it('asks before leaving a section with unsaved changes, and lets you stay or discard', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=candidate' })
    await userEvent.type(await screen.findByLabelText('Full name'), 'Half typed')

    await goTo(/3 Verification/)
    const dialog = await screen.findByRole('dialog', { name: 'Unsaved changes' })
    expect(screen.getByLabelText('Full name')).toHaveValue('Half typed')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Stay and keep editing' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Full name')).toHaveValue('Half typed')

    await goTo(/3 Verification/)
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Discard changes' }))
    expect(await screen.findByRole('form', { name: '3. Verification period' })).toBeInTheDocument()
  })

  it('does not ask when nothing was changed', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=candidate' })
    await screen.findByLabelText('Full name')

    await goTo(/3 Verification/)
    expect(await screen.findByRole('form', { name: '3. Verification period' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('does not ask after a successful save', async () => {
    const setup = serve(caseFixture(), {
      'PUT /api/cases/c-1/verification-period': () => {
        setup.current = caseFixture({ version: 1, period: { show: false, start: null, end: null } })
        return { body: setup.current }
      },
    })
    open(setup, { route: '/cases/c-1?section=verification-period' })

    await userEvent.click(await screen.findByRole('checkbox', { name: /Show the verification period/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await screen.findByText('Saved')

    await goTo(/5 Overview/)
    expect(await screen.findByRole('form', { name: '5. Overview & status' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('asks before leaving the case for another page', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=candidate' })
    await userEvent.type(await screen.findByLabelText('Full name'), 'typing')

    await userEvent.click(screen.getByRole('link', { name: 'Cases' }))
    const dialog = await screen.findByRole('dialog', { name: 'Unsaved changes' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Discard changes' }))
    expect(await screen.findByText('the case list')).toBeInTheDocument()
  })

  // ---- read-only -----------------------------------------------------------------------------------------------

  it('is read-only while the case is in review', async () => {
    const setup = serve(caseFixture({ lifecycle: 'IN_REVIEW', editable: false }))
    open(setup, { route: '/cases/c-1?section=candidate' })

    expect(await screen.findByText(/locked for editing/)).toBeInTheDocument()
    expect(screen.getByLabelText('Full name')).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  })

  it('is read-only for an admin who may read but not change the case', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=candidate', permissions: ['CASE_READ_ASSIGNED'] })

    expect(await screen.findByText('You can read this case but not change it.')).toBeInTheDocument()
    expect(screen.getByLabelText('Full name')).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  })

  it('shows the reviewer\'s comment when changes were requested', async () => {
    const setup = serve(caseFixture({ lifecycle: 'CHANGES_REQUESTED', reviewComment: 'Please add the PAN details.' }))
    open(setup)
    expect(await screen.findByText(/Please add the PAN details/)).toBeInTheDocument()
    expect(screen.getByText('Changes requested')).toBeInTheDocument()
  })

  // ---- the other sections -----------------------------------------------------------------------------------------

  it('fills in the standard status wording when a preset is chosen, and sends empty overrides as none', async () => {
    const setup = serve(caseFixture(), { 'PUT /api/cases/c-1/overview': () => ({ body: setup.current }) })
    open(setup, { route: '/cases/c-1?section=overview' })

    expect(await screen.findByLabelText('Total verifications')).toHaveAttribute('placeholder', '0')
    expect(screen.getByLabelText('Overall status')).toHaveAttribute('placeholder', 'Pending')
    await userEvent.click(screen.getByRole('radio', { name: 'Unable to Verify' }))
    expect(screen.getByLabelText('Status title')).toHaveValue('Unable to Verify')
    expect(screen.getByLabelText('Status subtitle')).toHaveValue('Unable to Complete Verification')
    expect(screen.getByLabelText('Status preview')).toHaveTextContent('Unable to Complete Verification')

    await userEvent.type(screen.getByLabelText('Total verifications'), '9')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(setup.puts()).toHaveLength(1))
    expect(setup.puts()[0]!.body).toMatchObject({
      statusPreset: 'UNABLE',
      statusTitle: 'Unable to Verify',
      totalOverride: 9,
      completedOverride: null,
      overallStatusOverride: null,
    })
  })

  it('refuses a non-numeric override before sending', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=overview' })
    await userEvent.type(await screen.findByLabelText('Total verifications'), 'many')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Enter a whole number, or leave empty')).toBeInTheDocument()
    expect(setup.puts()).toHaveLength(0)
  })

  it('needs watermark text only when the watermark is on', async () => {
    const setup = serve(caseFixture(), { 'PUT /api/cases/c-1/settings': () => ({ body: setup.current }) })
    open(setup, { route: '/cases/c-1?section=settings' })

    const text = await screen.findByLabelText('Watermark text')
    expect(text).toBeDisabled()
    await userEvent.click(screen.getByRole('checkbox', { name: /Print a diagonal watermark/ }))
    expect(text).toBeEnabled()
    await userEvent.clear(text)
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Enter the watermark text, or switch the watermark off')).toBeInTheDocument()
    expect(setup.puts()).toHaveLength(0)

    await userEvent.type(text, 'CONFIDENTIAL')
    await userEvent.click(screen.getByRole('radio', { name: /6 cards/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(setup.puts()).toHaveLength(1))
    expect(setup.puts()[0]!.body).toMatchObject({ layoutCards: 6, dateFormat: 'NUMERIC', watermarkEnabled: true, watermarkText: 'CONFIDENTIAL' })
  })

  it('saves remarks as entered', async () => {
    const setup = serve(caseFixture(), { 'PUT /api/cases/c-1/remarks': () => ({ body: setup.current }) })
    open(setup, { route: '/cases/c-1?section=remarks' })

    await userEvent.type(await screen.findByLabelText('Analyst remarks'), 'All good')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(setup.puts()).toHaveLength(1))
    expect(setup.puts()[0]!.body).toMatchObject({ analystRemarks: 'All good', finalRecommendation: null })
  })

  it('changes the report info, keeping an inactive current client selectable', async () => {
    const inactive = { ...CLIENTS[0]!, active: false }
    const setup = serve(caseFixture(), {
      'GET /api/clients': () => ({ body: [inactive, CLIENTS[1]] }),
      'PUT /api/cases/c-1/report-info': () => ({ body: setup.current }),
    })
    open(setup)

    const client = await screen.findByLabelText('Client')
    await waitFor(() => expect(within(client).getByRole('option', { name: 'Acme Corp' })).toBeInTheDocument())
    await userEvent.selectOptions(client, 'cl-2')
    await userEvent.clear(screen.getByLabelText('Report ID'))
    await userEvent.type(screen.getByLabelText('Report ID'), 'NX-2026-0100')
    await userEvent.type(screen.getByLabelText('Company name on the report'), 'Beta{Enter}Holdings')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(setup.puts()).toHaveLength(1))
    expect(setup.puts()[0]!.body).toMatchObject({ reportId: 'NX-2026-0100', clientId: 'cl-2', companyDisplayName: 'Beta\nHoldings', dueDate: null })
  })

  it('refuses a malformed Report ID before sending', async () => {
    const setup = serve(caseFixture())
    open(setup)
    const id = await screen.findByLabelText('Report ID')
    await userEvent.clear(id)
    await userEvent.type(id, 'no good!')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText(/Use 3-30 letters, digits/)).toBeInTheDocument()
    expect(setup.puts()).toHaveLength(0)
  })

  // ---- checks and generate ---------------------------------------------------------------------------------------------

  it('shows the checks section, saying a case with no checks cannot be submitted', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=checks' })
    expect(await screen.findByText(/No checks yet/)).toBeInTheDocument()
  })

  it('lists what blocks the report and jumps to the section that needs work', async () => {
    const setup = serve(caseFixture())
    open(setup, { route: '/cases/c-1?section=generate', permissions: [...EDITOR, 'REPORT_GENERATE'] })

    const errors = await screen.findByRole('region', { name: 'Errors' })
    expect(within(errors).getByText("Candidate's full name is required.")).toBeInTheDocument()
    expect(within(errors).getByText('Add at least one verification check.')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Warnings' })).getByText('Analyst remarks are empty.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit for review' })).not.toBeInTheDocument() // the server says this admin cannot submit
    expect(screen.getByRole('button', { name: 'Generate draft PDF' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Preview' })).toBeEnabled()

    await userEvent.click(within(errors).getAllByRole('button', { name: 'Go to section' })[0]!)
    expect(await screen.findByRole('form', { name: '2. Candidate details' })).toBeInTheDocument()
  })

  // ---- assignments -------------------------------------------------------------------------------------------------------

  it('shows who is assigned, and only assigners can change it', async () => {
    const setup = serve(caseFixture())
    open(setup)
    const panel = await screen.findByRole('region', { name: 'Assignments' })
    expect(within(panel).getByText('Ann Analyst')).toBeInTheDocument()
    expect(within(panel).getByText('Preparer')).toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Assign' })).not.toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument()
  })

  it('assigns a reviewer and removes an assignment', async () => {
    const setup = serve(caseFixture(), {
      'POST /api/cases/c-1/assignments': () => {
        setup.current = caseFixture({
          assignments: [...caseFixture().assignments, { adminId: 'a-2', fullName: 'Bob Reviewer', email: 'b@example.com', role: 'REVIEWER', assignedAt: 'x' }],
        })
        return { body: setup.current }
      },
      'DELETE /api/cases/c-1/assignments/a-2?role=REVIEWER': () => {
        setup.current = caseFixture()
        return { body: setup.current }
      },
    })
    open(setup, { permissions: [...EDITOR, 'CASE_ASSIGN'] })
    const panel = await screen.findByRole('region', { name: 'Assignments' })
    await waitFor(() => expect(within(panel).getByRole('option', { name: 'Bob Reviewer' })).toBeInTheDocument())

    await userEvent.selectOptions(within(panel).getByLabelText('Admin to assign'), 'a-2')
    await userEvent.click(within(panel).getByRole('button', { name: 'Assign' }))
    const people = within(within(panel).getByRole('list'))
    expect(await people.findByText('Bob Reviewer')).toBeInTheDocument()
    const post = setup.fake.mock.calls.find(([url, init]) => url === '/api/cases/c-1/assignments' && init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ adminId: 'a-2', role: 'REVIEWER' })

    await userEvent.click(within(panel).getByRole('button', { name: 'Remove Bob Reviewer as reviewer' }))
    await waitFor(() => expect(people.queryByText('Bob Reviewer')).not.toBeInTheDocument())
  })

  it('shows the server\'s reason when an assignment is refused', async () => {
    const setup = serve(caseFixture(), {
      'POST /api/cases/c-1/assignments': () => ({ status: 409, body: { code: 'CONFLICT', message: 'This admin already has the other role on this case.' } }),
    })
    open(setup, { permissions: [...EDITOR, 'CASE_ASSIGN'] })
    const panel = await screen.findByRole('region', { name: 'Assignments' })
    await waitFor(() => expect(within(panel).getByRole('option', { name: 'Ann Analyst' })).toBeInTheDocument())

    await userEvent.selectOptions(within(panel).getByLabelText('Admin to assign'), 'a-1')
    await userEvent.click(within(panel).getByRole('button', { name: 'Assign' }))
    expect(await within(panel).findByRole('alert')).toHaveTextContent('already has the other role')
  })
})
