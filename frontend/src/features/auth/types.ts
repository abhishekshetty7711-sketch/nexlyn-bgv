/** Answer of the password step and of accepting an invitation. */
export interface Challenge {
  status: '2FA_REQUIRED' | '2FA_SETUP_REQUIRED'
  challengeToken: string
  expiresInSeconds: number
}

/** Answer of a completed login or a refresh. `backupCodes` only comes with first-time 2FA setup. */
export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
  backupCodes?: string[]
}

export interface TwoFactorSetupInfo {
  secret: string
  otpauthUri: string
}

/** `GET /api/me`: everything the UI needs to know about the signed-in admin. */
export interface Me {
  id: string
  email: string
  fullName: string
  roles: string[]
  permissions: string[]
  mfaEnabled: boolean
  lastLoginAt: string | null
}
