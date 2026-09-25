// Safety guards for the demo-data script. It must never touch a real installation.
//
// 1. The address must be this computer (http, localhost / 127.0.0.1 / ::1). Checked BEFORE anything is sent, so
//    a password can never be typed into a script that is pointed at a real server by mistake.
// 2. The backend must be running with the "local" profile. That profile (and not the production one) lists the
//    info endpoint under /actuator besides health; the production profile exposes health alone. Checked after sign-in
//    and BEFORE the first write, so even a tunnel to a real server on localhost is refused.
//
// Everything the script creates is also clearly fake (report IDs start with DEMO-, documents say SPECIMEN).

const LOOPBACK = new Set(['localhost', '127.0.0.1', '[::1]', '::1'])

/** Returns the parsed address, or throws if it is not plain http on this computer. */
export function assertLocalTarget(baseUrl) {
  let url
  try {
    url = new URL(baseUrl)
  } catch {
    throw new Error(`"${baseUrl}" is not a valid address.`)
  }
  if (url.protocol !== 'http:') {
    throw new Error(`Refusing ${url.protocol}//${url.host}: the demo data goes only to a local http backend (a real site uses https).`)
  }
  if (!LOOPBACK.has(url.hostname)) {
    throw new Error(`Refusing ${url.hostname}: the demo data goes only to this computer (localhost / 127.0.0.1).`)
  }
  return url
}

/**
 * Throws unless the signed-in backend is the local-profile one. `probe` returns { status, endpoints }: the HTTP status of
 * GET /actuator and the names of the endpoints it lists. The local profile exposes health and info; the
 * production profile lists health alone (application-prod.yml).
 */
export async function assertLocalProfile(probe) {
  const { status, endpoints = [] } = await probe()
  if (status === 403) {
    throw new Error('Refusing to write: this administrator may not read /actuator, which the guard needs. Sign in as a SUPER_ADMIN.')
  }
  if (status !== 200) {
    throw new Error(`Refusing to write: /actuator answered ${status}, expected 200, so this does not look like a local backend.`)
  }
  if (!endpoints.includes('info')) {
    throw new Error(
      'Refusing to write: the backend exposes only ' + (endpoints.join(', ') || 'nothing') + ' under /actuator. ' +
        'The local profile also exposes info; the production profile does not, so this may be a real installation.',
    )
  }
}
