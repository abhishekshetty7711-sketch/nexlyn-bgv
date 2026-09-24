import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { caseFixture } from '../cases/testFixtures'
import { fakeAuth, mockFetch, renderRoutes, type FakeHandler } from '@/test/testUtils'
import { documentFixture } from './testFixtures'
import { PhotoUploader } from './PhotoUploader'

const withPhoto = () => caseFixture({ candidate: { ...caseFixture().candidate, hasPhoto: true, photoDocumentId: 'photo-1' } })

function show(caseView = caseFixture(), permissions = ['DOCUMENT_UPLOAD', 'DOCUMENT_DELETE'], handlers: Record<string, FakeHandler> = {}) {
  const fake = mockFetch({
    'GET /api/documents/photo-1/content': () => ({ body: { pretend: 'photo bytes' } }),
    'GET /api/cases/c-1': () => ({ body: caseView }),
    ...handlers,
  })
  renderRoutes([{ path: '/', element: <PhotoUploader caseView={caseView} /> }], { auth: fakeAuth({ permissions }) })
  return fake
}

const photoFile = (name = 'me.jpg', type = 'image/jpeg') => new File([new Uint8Array([1, 2, 3])], name, { type })

describe('PhotoUploader', () => {
  it('says there is no photo yet and offers an upload', () => {
    show()
    expect(screen.getByText('No photo')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Upload photo' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove photo' })).not.toBeInTheDocument()
  })

  it('shows the photo on file with replace and remove', async () => {
    show(withPhoto())
    expect(await screen.findByRole('img', { name: 'Candidate photo' })).toHaveAttribute('src', expect.stringMatching(/^data:/))
    expect(screen.getByRole('button', { name: 'Replace photo' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remove photo' })).toBeInTheDocument()
  })

  it('uploads the chosen picture at once', async () => {
    const fake = show(caseFixture(), undefined, { 'POST /api/cases/c-1/candidate/photo': () => ({ status: 201, body: documentFixture({ kind: 'PHOTO' }) }) })
    await userEvent.upload(screen.getByLabelText('Choose a photo'), photoFile())
    await waitFor(() => expect(fake.mock.calls.some(([url, init]) => url === '/api/cases/c-1/candidate/photo' && init?.method === 'POST')).toBe(true))
    const call = fake.mock.calls.find(([url, init]) => url === '/api/cases/c-1/candidate/photo' && init?.method === 'POST')!
    expect(((call[1]!.body as FormData).get('file') as File).name).toBe('me.jpg')
  })

  it('refuses a PDF or an oversized file before sending', async () => {
    const fake = show()
    await userEvent.upload(screen.getByLabelText('Choose a photo'), new File([new Uint8Array([1])], 'cv.pdf', { type: 'application/pdf' }), { applyAccept: false })
    expect(await screen.findByRole('alert')).toHaveTextContent('cv.pdf is not a JPEG or PNG picture.')
    expect(fake.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })

  it("shows the server's reason when the picture is refused", async () => {
    show(caseFixture(), undefined, {
      'POST /api/cases/c-1/candidate/photo': () => ({ status: 415, body: { code: 'UNSUPPORTED_FILE', message: 'That image could not be read.' } }),
    })
    await userEvent.upload(screen.getByLabelText('Choose a photo'), photoFile())
    expect(await screen.findByRole('alert')).toHaveTextContent('That image could not be read.')
  })

  it('removes the photo', async () => {
    const fake = show(withPhoto(), undefined, { 'DELETE /api/cases/c-1/candidate/photo': () => ({ status: 204 }) })
    await userEvent.click(await screen.findByRole('button', { name: 'Remove photo' }))
    await waitFor(() => expect(fake.mock.calls.some(([url, init]) => url === '/api/cases/c-1/candidate/photo' && init?.method === 'DELETE')).toBe(true))
  })

  it('offers only what the person may do', () => {
    show(withPhoto(), ['DOCUMENT_UPLOAD'])
    expect(screen.getByRole('button', { name: 'Replace photo' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove photo' })).not.toBeInTheDocument()
  })

  it('has no buttons on a locked case, or without permission', () => {
    show(caseFixture({ editable: false, lifecycle: 'IN_REVIEW' }))
    expect(screen.queryByRole('button', { name: 'Upload photo' })).not.toBeInTheDocument()
  })

  it('has no upload without the permission', () => {
    show(caseFixture(), [])
    expect(screen.queryByRole('button', { name: 'Upload photo' })).not.toBeInTheDocument()
  })
})
