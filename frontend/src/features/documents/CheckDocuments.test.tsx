import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes, type FakeHandler } from '@/test/testUtils'
import { CheckDocuments } from './CheckDocuments'
import { documentFixture, pdfFixture } from './testFixtures'
import type { DocumentView } from './types'

interface Setup {
  fake: ReturnType<typeof mockFetch>
  calls: (method: string, pathPart: string) => { url: string; body: unknown; form: FormData | null }[]
}

function serve(list: DocumentView[], extra: Record<string, FakeHandler> = {}): Setup {
  const fake = mockFetch({
    'GET /api/checks/ck-1/documents': () => ({ body: list }),
    'GET /api/documents/doc-1/content': () => ({ body: { pretend: 'image bytes' } }),
    ...extra,
  })
  return {
    fake,
    calls: (method, part) =>
      fake.mock.calls
        .filter(([url, init]) => (init?.method ?? 'GET') === method && String(url).includes(part))
        .map(([url, init]) => ({
          url: String(url),
          body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
          form: init?.body instanceof FormData ? init.body : null,
        })),
  }
}

function show(options: { canUpload?: boolean; canDelete?: boolean } = {}) {
  return renderRoutes(
    [{ path: '/', element: <CheckDocuments caseId="c-1" checkId="ck-1" canUpload={options.canUpload ?? true} canDelete={options.canDelete ?? true} /> }],
    { auth: fakeAuth() },
  )
}

const pickerOf = () => screen.getByLabelText('Upload documents') as HTMLInputElement
const picture = (name = 'scan.jpg') => new File([new Uint8Array([1, 2, 3])], name, { type: 'image/jpeg' })

describe('CheckDocuments', () => {
  it('lists documents with their label, size, quality and a preview', async () => {
    serve([documentFixture(), pdfFixture()])
    show()

    const list = await screen.findByRole('list', { name: 'Documents of this check' })
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('Original Document')
    expect(items[0]).toHaveTextContent('degree.jpg · 244 KB · 1600×1200')
    expect(within(items[0]!).getByText('High quality')).toBeInTheDocument()
    expect(await within(items[0]!).findByRole('img', { name: 'Preview of Original Document' })).toHaveAttribute('src', expect.stringMatching(/^data:/))
    expect(items[1]).toHaveTextContent('Additional Document 1')
    expect(items[1]).toHaveTextContent('PDF')
    expect(within(items[1]!).queryByText(/quality/)).not.toBeInTheDocument()
  })

  it('shows the state of each picture: new page, larger box, cropped', async () => {
    serve([documentFixture({ moveToNextPage: true, useLargerBox: true, crop: { x: 0, y: 0, width: 0.5, height: 0.5 }, quality: 'LOW' })])
    show()
    expect(await screen.findByText('New page')).toBeInTheDocument()
    expect(screen.getByText('Larger box')).toBeInTheDocument()
    expect(screen.getByText('Cropped')).toBeInTheDocument()
    expect(screen.getByText('Low quality')).toBeInTheDocument()
  })

  it('says what is missing when there are no documents', async () => {
    serve([])
    show()
    expect(await screen.findByText(/No documents attached yet/)).toBeInTheDocument()
  })

  // ---- uploading ---------------------------------------------------------------------------------------

  it('uploads each chosen file as a supporting document', async () => {
    const setup = serve([], { 'POST /api/checks/ck-1/documents': () => ({ status: 201, body: documentFixture() }) })
    show()
    await screen.findByText(/No documents attached yet/)

    await userEvent.upload(pickerOf(), [picture('a.jpg'), picture('b.jpg')])

    await waitFor(() => expect(setup.calls('POST', '/documents')).toHaveLength(2))
    const [first] = setup.calls('POST', '/documents')
    expect(first!.url).toBe('/api/checks/ck-1/documents?kind=CHECK_DOC')
    expect((first!.form!.get('file') as File).name).toBe('a.jpg')
  })

  it('checks a file before sending and reports each problem', async () => {
    const setup = serve([])
    show()
    await screen.findByText(/No documents attached yet/)

    const gif = new File([new Uint8Array([1])], 'anim.gif', { type: 'image/gif' })
    fireEvent.change(pickerOf(), { target: { files: [gif] } })

    expect(await screen.findByText('anim.gif is not a JPEG, PNG or PDF file.')).toBeInTheDocument()
    expect(setup.calls('POST', '/documents')).toHaveLength(0)
  })

  it("shows the server's reason when a file is refused, and still tries the others", async () => {
    let attempt = 0
    const setup = serve([], {
      'POST /api/checks/ck-1/documents': () => {
        attempt += 1
        return attempt === 1
          ? { status: 415, body: { code: 'UNSUPPORTED_FILE', message: 'That image could not be read.' } }
          : { status: 201, body: documentFixture() }
      },
    })
    show()
    await screen.findByText(/No documents attached yet/)

    await userEvent.upload(pickerOf(), [picture('bad.jpg'), picture('good.jpg')])

    expect(await screen.findByText('bad.jpg: That image could not be read.')).toBeInTheDocument()
    await waitFor(() => expect(setup.calls('POST', '/documents')).toHaveLength(2))
  })

  // ---- changing ---------------------------------------------------------------------------------------

  it('reorders with the arrow buttons', async () => {
    const setup = serve([documentFixture(), pdfFixture()], { 'PATCH /api/checks/ck-1/documents/order': () => ({ body: [] }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Move Additional Document 1 up' }))
    await waitFor(() => expect(setup.calls('PATCH', '/order')).toHaveLength(1))
    expect(setup.calls('PATCH', '/order')[0]!.body).toEqual({ ids: ['doc-2', 'doc-1'] })
  })

  it('asks before removing, then removes', async () => {
    const setup = serve([documentFixture()], { 'DELETE /api/documents/doc-1': () => ({ status: 204 }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Remove Original Document' }))
    const dialog = await screen.findByRole('dialog', { name: 'Remove this document?' })
    expect(setup.calls('DELETE', '/documents/doc-1')).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove document' }))
    await waitFor(() => expect(setup.calls('DELETE', '/documents/doc-1')).toHaveLength(1))
  })

  it('shows only what the person may do', async () => {
    serve([documentFixture()])
    show({ canUpload: false, canDelete: false })
    await screen.findByRole('list', { name: 'Documents of this check' })
    expect(screen.queryByLabelText('Upload documents')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Move / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Remove / })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View Original Document' })).toBeInTheDocument()
  })

  it('lets someone who cannot delete still add and edit', async () => {
    serve([documentFixture()])
    show({ canUpload: true, canDelete: false })
    await screen.findByRole('list', { name: 'Documents of this check' })
    expect(screen.getByRole('button', { name: 'Edit Original Document' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Remove / })).not.toBeInTheDocument()
  })

  // ---- the editor --------------------------------------------------------------------------------------

  it('edits the label and crop of a picture and keeps its page options as they are', async () => {
    const setup = serve([documentFixture({ version: 3, moveToNextPage: true, useLargerBox: true })], { 'PUT /api/documents/doc-1': () => ({ body: documentFixture({ version: 4 }) }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Original Document' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit Original Document' })

    await userEvent.type(within(dialog).getByLabelText('Label'), '  Degree certificate ')
    expect(within(dialog).queryByLabelText(/new page|larger box/i)).not.toBeInTheDocument() // the page switches are on the row, not here
    fireEvent.change(within(dialog).getByLabelText('Width'), { target: { value: '60' } })
    fireEvent.change(within(dialog).getByLabelText('Height'), { target: { value: '50' } })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-1')).toHaveLength(1))
    expect(setup.calls('PUT', '/documents/doc-1')[0]!.body).toEqual({
      label: 'Degree certificate',
      moveToNextPage: true,
      useLargerBox: true,
      crop: { x: 0, y: 0, width: 0.6, height: 0.5 },
      version: 3,
    })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('stores "the whole picture" as no crop, and lets a crop be cleared', async () => {
    const setup = serve([documentFixture({ crop: { x: 0.1, y: 0.1, width: 0.5, height: 0.5 } })], { 'PUT /api/documents/doc-1': () => ({ body: documentFixture() }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Original Document' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByTestId('crop-box')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Use the whole picture' }))
    expect(within(dialog).queryByTestId('crop-box')).not.toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-1')).toHaveLength(1))
    expect((setup.calls('PUT', '/documents/doc-1')[0]!.body as { crop: unknown }).crop).toBeNull()
  })

  it('draws a crop by dragging on the picture', async () => {
    const setup = serve([documentFixture()], { 'PUT /api/documents/doc-1': () => ({ body: documentFixture() }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Original Document' }))
    const dialog = await screen.findByRole('dialog')
    const frame = within(dialog).getByTestId('crop-frame')
    frame.getBoundingClientRect = () => ({ left: 0, top: 0, width: 400, height: 300, right: 400, bottom: 300, x: 0, y: 0, toJSON: () => ({}) })

    fireEvent.pointerDown(frame, { clientX: 100, clientY: 60, pointerId: 1 })
    fireEvent.pointerMove(frame, { clientX: 300, clientY: 240, pointerId: 1 })
    fireEvent.pointerUp(frame, { clientX: 300, clientY: 240, pointerId: 1 })

    expect(within(dialog).getByLabelText('Left edge')).toHaveValue('25')
    expect(within(dialog).getByLabelText('Width')).toHaveValue('50')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-1')).toHaveLength(1))
    const crop = (setup.calls('PUT', '/documents/doc-1')[0]!.body as { crop: { x: number; y: number; width: number; height: number } }).crop
    expect(crop.x).toBeCloseTo(0.25)
    expect(crop.y).toBeCloseTo(0.2)
    expect(crop.width).toBeCloseTo(0.5)
    expect(crop.height).toBeCloseTo(0.6)
  })

  it('offers no crop for a PDF', async () => {
    serve([pdfFixture()])
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Additional Document 1' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByTestId('crop-frame')).not.toBeInTheDocument()
  })

  // ---- Move to Next Page (feature 25): a switch on every row, saved at once -------------------------------

  it('shows a Move to Next Page switch with help text on every document, off by default', async () => {
    serve([documentFixture(), pdfFixture()])
    show()
    const list = await screen.findByRole('list', { name: 'Documents of this check' })
    for (const name of ['Original Document', 'Additional Document 1']) {
      const group = within(list).getByRole('group', { name: `Page options for ${name}` })
      const toggle = within(group).getByRole('button', { name: `Move ${name} to the next page` })
      expect(toggle).toHaveAttribute('aria-pressed', 'false')
      expect(toggle).toHaveTextContent('→ Move to Next Page')
      expect(group).toHaveTextContent(/page of its own/)
    }
  })

  it('saves the switch the moment it is clicked and keeps the rest of the document as it was', async () => {
    const crop = { x: 0.1, y: 0.1, width: 0.5, height: 0.5 }
    const setup = serve([documentFixture({ version: 2, label: 'Degree', crop })], { 'PUT /api/documents/doc-1': () => ({ body: documentFixture({ version: 3, moveToNextPage: true }) }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Move Original Document to the next page' }))
    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-1')).toHaveLength(1))
    expect(setup.calls('PUT', '/documents/doc-1')[0]!.body).toEqual({ label: 'Degree', moveToNextPage: true, useLargerBox: false, crop, version: 2 })
  })

  it('shows "On Next Page" when a document is moved, and switches it back off with one click', async () => {
    const setup = serve([documentFixture({ moveToNextPage: true })], { 'PUT /api/documents/doc-1': () => ({ body: documentFixture() }) })
    show()
    const toggle = await screen.findByRole('button', { name: 'Move Original Document to the next page' })
    expect(toggle).toHaveAttribute('aria-pressed', 'true')
    expect(toggle).toHaveTextContent('✓ On Next Page')
    await userEvent.click(toggle)
    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-1')).toHaveLength(1))
    expect((setup.calls('PUT', '/documents/doc-1')[0]!.body as { moveToNextPage: boolean }).moveToNextPage).toBe(false)
  })

  it('works for a PDF too', async () => {
    const setup = serve([pdfFixture()], { 'PUT /api/documents/doc-2': () => ({ body: pdfFixture({ moveToNextPage: true }) }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Move Additional Document 1 to the next page' }))
    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-2')).toHaveLength(1))
    expect((setup.calls('PUT', '/documents/doc-2')[0]!.body as { moveToNextPage: boolean }).moveToNextPage).toBe(true)
  })

  it('shows the change on screen after saving', async () => {
    let moved = false
    serve([documentFixture()], {
      'GET /api/checks/ck-1/documents': () => ({ body: [documentFixture({ moveToNextPage: moved })] }),
      'PUT /api/documents/doc-1': () => {
        moved = true
        return { body: documentFixture({ moveToNextPage: true }) }
      },
    })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Move Original Document to the next page' }))
    expect(await screen.findByRole('button', { name: 'Move Original Document to the next page', pressed: true })).toHaveTextContent('✓ On Next Page')
    expect(screen.getByText('New page')).toBeInTheDocument()
  })

  it("shows the server's reason when the switch cannot be saved", async () => {
    serve([documentFixture()], { 'PUT /api/documents/doc-1': () => ({ status: 409, body: { code: 'CONFLICT', message: 'This document was changed by someone else. Reload it and try again.' } }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Move Original Document to the next page' }))
    expect(await screen.findByText(/changed by someone else/)).toBeInTheDocument()
  })

  it('offers the switch only to people who may change documents', async () => {
    serve([documentFixture()])
    show({ canUpload: false })
    await screen.findByRole('list', { name: 'Documents of this check' })
    expect(screen.queryByRole('group', { name: /Page options/ })).not.toBeInTheDocument()
  })

  // ---- Use Larger Box (feature 26): a second switch, tied to the first as in the reference tool ------------

  const putBody = (setup: Setup, id = 'doc-1') => setup.calls('PUT', `/documents/${id}`)[0]!.body

  it('shows a Use Larger Box switch with help text on every picture, off by default', async () => {
    serve([documentFixture()])
    show()
    const toggle = await screen.findByRole('button', { name: 'Use a larger box for Original Document' })
    expect(toggle).toHaveAttribute('aria-pressed', 'false')
    expect(toggle).toHaveTextContent('☐ Use Larger Box')
    expect(within(screen.getByRole('group', { name: 'Page options for Original Document' })).getByText(/nearly as tall as the page/)).toBeInTheDocument()
  })

  it('switching the larger box on also moves the document to the next page, and saves at once', async () => {
    const crop = { x: 0.1, y: 0.1, width: 0.5, height: 0.5 }
    const setup = serve([documentFixture({ version: 5, label: 'Court screenshot', crop })], { 'PUT /api/documents/doc-1': () => ({ body: documentFixture({ version: 6, moveToNextPage: true, useLargerBox: true }) }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Use a larger box for Original Document' }))
    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-1')).toHaveLength(1))
    expect(putBody(setup)).toEqual({ label: 'Court screenshot', moveToNextPage: true, useLargerBox: true, crop, version: 5 })
  })

  it('shows "Larger Box (Next Page)" when on, and switching it off leaves the document on its own page', async () => {
    const setup = serve([documentFixture({ moveToNextPage: true, useLargerBox: true })], { 'PUT /api/documents/doc-1': () => ({ body: documentFixture({ moveToNextPage: true }) }) })
    show()
    const toggle = await screen.findByRole('button', { name: 'Use a larger box for Original Document' })
    expect(toggle).toHaveAttribute('aria-pressed', 'true')
    expect(toggle).toHaveTextContent('✓ Larger Box (Next Page)')
    await userEvent.click(toggle)
    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-1')).toHaveLength(1))
    expect(putBody(setup)).toMatchObject({ moveToNextPage: true, useLargerBox: false })
  })

  it('switching the move off while the larger box is on switches the larger box off too (it only exists on its own page)', async () => {
    const setup = serve([documentFixture({ moveToNextPage: true, useLargerBox: true })], { 'PUT /api/documents/doc-1': () => ({ body: documentFixture() }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Move Original Document to the next page' }))
    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-1')).toHaveLength(1))
    expect(putBody(setup)).toMatchObject({ moveToNextPage: false, useLargerBox: false })
  })

  it('switching the move on keeps the standard box', async () => {
    const setup = serve([documentFixture()], { 'PUT /api/documents/doc-1': () => ({ body: documentFixture({ moveToNextPage: true }) }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Move Original Document to the next page' }))
    await waitFor(() => expect(setup.calls('PUT', '/documents/doc-1')).toHaveLength(1))
    expect(putBody(setup)).toMatchObject({ moveToNextPage: true, useLargerBox: false })
  })

  it('offers no larger box for a PDF, but still the move', async () => {
    serve([pdfFixture()])
    show()
    await screen.findByRole('button', { name: 'Move Additional Document 1 to the next page' })
    expect(screen.queryByRole('button', { name: /larger box/i })).not.toBeInTheDocument()
  })

  it('shows both switches on after one click, on the screen and in the badges', async () => {
    let state = documentFixture()
    serve([state], {
      'GET /api/checks/ck-1/documents': () => ({ body: [state] }),
      'PUT /api/documents/doc-1': () => {
        state = documentFixture({ moveToNextPage: true, useLargerBox: true })
        return { body: state }
      },
    })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Use a larger box for Original Document' }))
    expect(await screen.findByRole('button', { name: 'Use a larger box for Original Document', pressed: true })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Move Original Document to the next page', pressed: true })).toBeInTheDocument()
    expect(screen.getByText('New page')).toBeInTheDocument()
    expect(screen.getByText('Larger box')).toBeInTheDocument()
  })

  it("shows the server's reason when the larger box cannot be saved", async () => {
    serve([documentFixture()], { 'PUT /api/documents/doc-1': () => ({ status: 409, body: { code: 'CONFLICT', message: 'The case is locked while it is in review.' } }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Use a larger box for Original Document' }))
    expect(await screen.findByText(/locked while it is in review/)).toBeInTheDocument()
  })

  it("shows the server's message when the edit cannot be saved", async () => {
    serve([documentFixture()], { 'PUT /api/documents/doc-1': () => ({ status: 409, body: { code: 'CONFLICT', message: 'This document was changed by someone else. Reload it and try again.' } }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Original Document' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByText(/changed by someone else/)).toBeInTheDocument()
  })

  // ---- the viewer and Undo ----------------------------------------------------------------------------

  it('shows a picture in a viewer that zooms from 50% to 300% and can be reset', async () => {
    serve([documentFixture()])
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'View Original Document' }))
    const dialog = await screen.findByRole('dialog', { name: 'View Original Document' })
    const level = within(dialog).getByRole('status', { name: 'Zoom level' })
    const picture = within(dialog).getByTestId('zoomed-picture')
    expect(level).toHaveTextContent('100%')
    expect(within(dialog).getByRole('button', { name: 'Reset zoom' })).toBeDisabled()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Zoom in' }))
    expect(level).toHaveTextContent('125%')
    expect(picture).toHaveStyle({ width: '125%' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reset zoom' }))
    expect(level).toHaveTextContent('100%')

    for (let i = 0; i < 4; i += 1) {
      await userEvent.click(within(dialog).getByRole('button', { name: 'Zoom out' }))
    }
    expect(level).toHaveTextContent('50%')
    expect(within(dialog).getByRole('button', { name: 'Zoom out' })).toBeDisabled()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Reset zoom' }))
    for (let i = 0; i < 8; i += 1) {
      await userEvent.click(within(dialog).getByRole('button', { name: 'Zoom in' }))
    }
    expect(level).toHaveTextContent('300%')
    expect(within(dialog).getByRole('button', { name: 'Zoom in' })).toBeDisabled()
  })

  it('closes the viewer with Escape and does not change the document', async () => {
    const setup = serve([documentFixture()])
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'View Original Document' }))
    await screen.findByRole('dialog', { name: 'View Original Document' })
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(setup.calls('PUT', '/documents/')).toHaveLength(0)
  })

  it('takes the crop changes back with Undo, to what is saved', async () => {
    serve([documentFixture({ crop: { x: 0.1, y: 0.1, width: 0.5, height: 0.5 } })])
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Original Document' }))
    const dialog = await screen.findByRole('dialog')
    const undo = within(dialog).getByRole('button', { name: 'Undo changes' })
    expect(undo).toBeDisabled()

    fireEvent.change(within(dialog).getByLabelText('Width'), { target: { value: '80' } })
    expect(undo).toBeEnabled()
    expect(within(dialog).getByLabelText('Width')).toHaveValue('80')
    await userEvent.click(undo)
    expect(within(dialog).getByLabelText('Width')).toHaveValue('50')
    expect(undo).toBeDisabled()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Use the whole picture' }))
    expect(within(dialog).queryByTestId('crop-box')).not.toBeInTheDocument()
    await userEvent.click(undo)
    expect(within(dialog).getByTestId('crop-box')).toBeInTheDocument()
  })

  // ---- opening ----------------------------------------------------------------------------------------

  it('opens a picture in a new tab from an in-memory copy, from the viewer', async () => {
    const setup = serve([documentFixture()])
    const opened: unknown[][] = []
    window.open = ((...args: unknown[]) => (opened.push(args), null)) as typeof window.open
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'View Original Document' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'View Original Document' })).getByRole('button', { name: 'Open in a new tab' }))
    await waitFor(() => expect(opened).toHaveLength(1))
    expect(String(opened[0]![0])).toMatch(/^blob:/)
    expect(opened[0]![2]).toBe('noopener')
    expect(setup.calls('GET', '/documents/doc-1/content').length).toBeGreaterThan(0)
  })
})
