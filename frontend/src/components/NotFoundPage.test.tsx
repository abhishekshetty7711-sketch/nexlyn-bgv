import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ForbiddenPage } from '@/features/auth/ForbiddenPage'
import { renderRoutes } from '@/test/testUtils'
import { NotFoundPage } from './NotFoundPage'

describe('the two "nothing here" pages', () => {
  it('Page not found has a heading, a way back and names the tab', () => {
    renderRoutes([{ path: '*', element: <NotFoundPage /> }], { route: '/nowhere' })
    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the dashboard' })).toHaveAttribute('href', '/')
    expect(document.title).toBe('Page not found - Nexlyn BGV')
  })

  it('Not allowed has a heading and names the tab', () => {
    renderRoutes([{ path: '*', element: <ForbiddenPage /> }])
    expect(screen.getByRole('heading', { level: 1, name: 'Not allowed' })).toBeInTheDocument()
    expect(document.title).toBe('Not allowed - Nexlyn BGV')
  })
})
