import { useEffect } from 'react'

export const APP_NAME = 'Nexlyn BGV'

/** "Cases - Nexlyn BGV". An empty name gives the bare app name. */
export function pageTitle(name?: string | null): string {
  return name ? `${name} - ${APP_NAME}` : APP_NAME
}

/**
 * Names the browser tab after the screen (WCAG 2.4.2 Page Titled): a screen-reader user, someone with many tabs
 * open and the browser history can then tell the pages apart. Pass nothing while the name is still loading.
 */
export function usePageTitle(name?: string | null): void {
  const title = pageTitle(name)
  useEffect(() => {
    document.title = title
  }, [title])
}
