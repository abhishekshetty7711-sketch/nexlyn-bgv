import type { StatusPreset } from '../types'

/** The four status pills of the report cover (CLAUDE.md section 6.2): default text and colours. */
export const PRESETS: Record<StatusPreset, { title: string; subtitle: string; colours: string }> = {
  COMPLETED: { title: 'Completed', subtitle: 'All Requested Verifications Completed', colours: 'border-emerald-300 bg-emerald-100 text-emerald-700' },
  DISCREPANCY: { title: 'Discrepancy', subtitle: 'Discrepancy Found in Verification', colours: 'border-red-300 bg-red-100 text-red-700' },
  UNABLE: { title: 'Unable to Verify', subtitle: 'Unable to Complete Verification', colours: 'border-amber-300 bg-amber-100 text-amber-800' },
  CLOSED: { title: 'Closed', subtitle: 'Verification Closed / Insufficient Data', colours: 'border-slate-300 bg-slate-100 text-slate-700' },
}
