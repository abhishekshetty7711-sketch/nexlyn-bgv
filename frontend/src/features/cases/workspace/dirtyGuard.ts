import { createContext, useContext, useEffect } from 'react'

/**
 * Lets the section on screen tell the workspace "I have unsaved changes", so leaving (another
 * section, another page, closing the tab) can ask first instead of silently losing typing.
 */
export interface DirtyGuard {
  setDirty: (dirty: boolean) => void
}

export const DirtyGuardContext = createContext<DirtyGuard | null>(null)

/** Call from a section's form with its `isDirty`. Clears itself when the section goes away. */
export function useReportDirty(isDirty: boolean): void {
  const guard = useContext(DirtyGuardContext)
  useEffect(() => {
    guard?.setDirty(isDirty)
    return () => guard?.setDirty(false)
  }, [guard, isDirty])
}
