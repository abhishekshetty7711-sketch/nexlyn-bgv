// A small client for the backend's REST API (the same calls the web app makes).

export class ApiError extends Error {
  constructor(method, path, status, body) {
    const detail = typeof body === 'object' && body ? `${body.code ?? ''} ${body.message ?? ''}`.trim() : String(body ?? '')
    const fields = body?.fieldErrors?.length ? ` [${body.fieldErrors.map((f) => `${f.field}: ${f.message}`).join('; ')}]` : ''
    const id = body?.correlationId ? ` (correlation id ${body.correlationId})` : ''
    super(`${method} ${path} -> ${status} ${detail}${fields}${id}`)
    this.status = status
    this.body = body
  }
}

export class Api {
  constructor(baseUrl) {
    this.base = baseUrl.replace(/\/$/, '')
    this.token = null
  }

  headers(extra = {}) {
    return this.token ? { Authorization: `Bearer ${this.token}`, ...extra } : extra
  }

  async raw(method, path, { json, form, headers } = {}) {
    const init = { method, headers: this.headers(headers) }
    if (json !== undefined) {
      init.headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(json)
    } else if (form) {
      init.body = form
    }
    return fetch(`${this.base}${path}`, init)
  }

  async call(method, path, options = {}) {
    const response = await this.raw(method, path, options)
    const type = response.headers.get('content-type') ?? ''
    if (!response.ok) {
      const body = type.includes('json') ? await response.json().catch(() => null) : await response.text().catch(() => '')
      throw new ApiError(method, path, response.status, body)
    }
    if (options.as === 'bytes') {
      return Buffer.from(await response.arrayBuffer())
    }
    if (response.status === 204 || !type.includes('json')) {
      return null
    }
    return response.json()
  }

  get(path, options) {
    return this.call('GET', path, options)
  }

  post(path, json) {
    return this.call('POST', path, { json: json ?? {} })
  }

  put(path, json) {
    return this.call('PUT', path, { json })
  }

  upload(path, filename, bytes, mimeType) {
    const form = new FormData()
    form.append('file', new Blob([bytes], { type: mimeType }), filename)
    return this.call('POST', path, { form })
  }

  /** Signs in with a password and a two-step code. Throws when the account has no two-step login yet. */
  async signIn(email, password, code) {
    const challenge = await this.call('POST', '/api/auth/login', { json: { email, password } })
    if (challenge.status !== '2FA_REQUIRED') {
      throw new Error(
        'This account has not set up two-step login yet. Sign in once in the web app (http://localhost:5173), scan the QR code, then run this script again.',
      )
    }
    const tokens = await this.call('POST', '/api/auth/2fa/verify', { json: { challengeToken: challenge.challengeToken, code } })
    this.token = tokens.accessToken
  }
}
