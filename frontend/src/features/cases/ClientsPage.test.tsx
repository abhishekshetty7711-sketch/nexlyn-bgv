import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeAuth, mockFetch, renderRoutes } from '@/test/testUtils'
import { ClientsPage } from './ClientsPage'

const CLIENTS = [
  { id: 'cl-1', name: 'Acme Corp', displayName: 'Acme Corp\nPrivate Limited', defaultCheckTypes: ['AADHAAR', 'PAN'], active: true, version: 4 },
  { id: 'cl-2', name: 'Beta Ltd', displayName: 'Beta Ltd', defaultCheckTypes: [], active: false, version: 0 },
]

function show(permissions = ['CLIENT_MANAGE']) {
  return renderRoutes([{ path: '/', element: <ClientsPage /> }], { auth: fakeAuth({ permissions }) })
}

describe('ClientsPage', () => {
  it('lists clients with their printed name and status', async () => {
    mockFetch({ 'GET /api/clients': () => ({ body: CLIENTS }) })
    show()

    const acme = (await screen.findByText('Acme Corp', { selector: 'td' })).closest('tr')!
    expect(within(acme).getByText('Active')).toBeInTheDocument()
    expect(acme).toHaveTextContent('Private Limited')
    expect(within(screen.getAllByText('Beta Ltd', { selector: 'td' })[0]!.closest('tr')!).getByText('Inactive')).toBeInTheDocument()
  })

  it('is read-only for someone who can only read cases', async () => {
    mockFetch({ 'GET /api/clients': () => ({ body: CLIENTS }) })
    show(['CASE_READ_ASSIGNED'])
    await screen.findByText('Acme Corp', { selector: 'td' })
    expect(screen.queryByRole('button', { name: 'New client' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
  })

  it('creates a client, turning the check list into codes', async () => {
    const fake = mockFetch({ 'GET /api/clients': () => ({ body: CLIENTS }), 'POST /api/clients': () => ({ status: 201, body: CLIENTS[0] }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'New client' }))
    const dialog = screen.getByRole('dialog', { name: 'New client' })

    await userEvent.type(within(dialog).getByLabelText('Client name'), ' Gamma Inc ')
    await userEvent.type(within(dialog).getByLabelText('Name on reports'), 'Gamma{Enter}Incorporated')
    await userEvent.type(within(dialog).getByLabelText(/Usual checks/), 'aadhaar, pan , ,education')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    await screen.findByText('Acme Corp', { selector: 'td' })
    const post = fake.mock.calls.find(([url, init]) => url === '/api/clients' && init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({
      name: 'Gamma Inc',
      displayName: 'Gamma\nIncorporated',
      defaultCheckTypes: ['AADHAAR', 'PAN', 'EDUCATION'],
      active: true,
    })
  })

  it('checks the form before saving', async () => {
    const fake = mockFetch({ 'GET /api/clients': () => ({ body: CLIENTS }) })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'New client' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Enter the client name')).toBeInTheDocument()
    expect(screen.getByText('Enter the name as it should print on reports')).toBeInTheDocument()
    expect(fake.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })

  it('edits a client with the version it was loaded with', async () => {
    const fake = mockFetch({ 'GET /api/clients': () => ({ body: CLIENTS }), 'PUT /api/clients/cl-1': () => ({ body: CLIENTS[0] }) })
    show()
    const acme = (await screen.findByText('Acme Corp', { selector: 'td' })).closest('tr')!
    await userEvent.click(within(acme).getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit Acme Corp' })
    expect(within(dialog).getByLabelText(/Usual checks/)).toHaveValue('AADHAAR, PAN')

    await userEvent.click(within(dialog).getByRole('checkbox', { name: /Active/ }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    await screen.findByText('Acme Corp', { selector: 'td' })
    const put = fake.mock.calls.find(([url, init]) => url === '/api/clients/cl-1' && init?.method === 'PUT')!
    expect(JSON.parse(put[1]!.body as string)).toMatchObject({ version: 4, name: 'Acme Corp', active: false, defaultCheckTypes: ['AADHAAR', 'PAN'] })
  })

  it('shows the server\'s reason when a client cannot be saved', async () => {
    mockFetch({
      'GET /api/clients': () => ({ body: CLIENTS }),
      'POST /api/clients': () => ({ status: 409, body: { code: 'CONFLICT', message: 'A client with this name already exists.' } }),
    })
    show()
    await userEvent.click(await screen.findByRole('button', { name: 'New client' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Client name'), 'Acme Corp')
    await userEvent.type(within(dialog).getByLabelText('Name on reports'), 'Acme')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('already exists')
  })
})
