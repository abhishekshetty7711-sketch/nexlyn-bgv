import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { progressFixture } from '../testFixtures'
import { ReportProgress } from './ReportProgress'

const counts = (over: Record<string, number>) => ({ VERIFIED: 0, DISCREPANCY: 0, UNABLE_TO_VERIFY: 0, CLOSED: 0, PENDING: 0, IN_PROGRESS: 0, ...over })

describe('ReportProgress', () => {
  it('shows zeros and no COMPLETE badge when the case has no checks', () => {
    render(<ReportProgress progress={progressFixture()} />)
    const box = screen.getByRole('region', { name: 'Report progress' })
    expect(within(box).getByText('Total checks').nextSibling).toHaveTextContent('0')
    expect(within(box).getAllByText('0 (0%)')).toHaveLength(6)
    expect(within(box).getByText('0 of 0 (0%)')).toBeInTheDocument()
    expect(screen.queryByText('✓ COMPLETE')).not.toBeInTheDocument()
  })

  it('shows the count and percentage of each of the six statuses and how many checks are finished', () => {
    render(
      <ReportProgress
        progress={progressFixture({ totalChecks: 5, checksByStatus: counts({ VERIFIED: 2, DISCREPANCY: 1, PENDING: 1, IN_PROGRESS: 1 }) })}
      />,
    )
    const box = screen.getByRole('region', { name: 'Report progress' })
    const row = (label: string) => within(box).getByText(label).closest('li') as HTMLElement
    expect(row('Verified')).toHaveTextContent('2 (40%)')
    expect(row('Discrepancy')).toHaveTextContent('1 (20%)')
    expect(row('Unable to Verify')).toHaveTextContent('0 (0%)')
    expect(row('Closed')).toHaveTextContent('0 (0%)')
    expect(row('Pending')).toHaveTextContent('1 (20%)')
    expect(row('In Progress')).toHaveTextContent('1 (20%)')
    // the finished ones are Verified + Discrepancy + Unable + Closed, as in the reference tool
    expect(within(box).getByText('3 of 5 (60%)')).toBeInTheDocument()
    expect(within(box).getByRole('progressbar', { name: 'Checks finished' })).toHaveAttribute('aria-valuenow', '60')
    expect(screen.queryByText('✓ COMPLETE')).not.toBeInTheDocument()
  })

  it('says COMPLETE when every check has an outcome', () => {
    render(<ReportProgress progress={progressFixture({ totalChecks: 3, checksByStatus: counts({ VERIFIED: 1, CLOSED: 1, UNABLE_TO_VERIFY: 1 }) })} />)
    expect(screen.getByText('✓ COMPLETE')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Checks finished' })).toHaveAttribute('aria-valuenow', '100')
  })

  it('draws the marks of the six statuses', () => {
    render(<ReportProgress progress={progressFixture({ totalChecks: 1, checksByStatus: counts({ VERIFIED: 1 }) })} />)
    for (const mark of ['✓', '✕', 'ⓘ', '−', '⏱', '↻']) {
      expect(screen.getAllByText(mark).length).toBeGreaterThan(0)
    }
  })

  it('renders while the progress is still loading', () => {
    render(<ReportProgress progress={undefined} />)
    expect(screen.getByText('0 of 0 (0%)')).toBeInTheDocument()
  })
})
