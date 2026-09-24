// The access token lives ONLY in this variable (CLAUDE.md §11.5): never in localStorage,
// sessionStorage, a cookie or the URL. It disappears on reload; the refresh cookie brings it back.
let accessToken: string | null = null

export const tokenStore = {
  get: (): string | null => accessToken,
  set: (token: string | null): void => {
    accessToken = token
  },
}
