#!/usr/bin/env node
// Fills a LOCAL Nexlyn BGV stack with FAKE demo cases (candidates, checks, photos, documents) and prints a
// PDF report for each one into scripts/demo-data/output/ so you can look at them.
//
//   node scripts/demo-data/seed-demo.mjs --email you@example.com
//
// It asks for your password and the current 6-digit code of your authenticator app (or read them from
// DEMO_ADMIN_PASSWORD / DEMO_ADMIN_CODE). Run it with the local stack up (./scripts/local-up.sh -d) and an
// administrator who has two-step login set up (SUPER_ADMIN: it needs to read /actuator).
//
// SAFETY (see lib/guard.mjs): it only talks to http://localhost, and it refuses to write anything unless the
// backend runs with the "local" profile. It cannot be pointed at a production installation.
// Running it twice does not duplicate cases: a report ID that already exists is skipped.
//
// Options:
//   --url http://localhost:8080   backend address (must be on this computer)
//   --email <address>             the administrator to sign in as (or DEMO_ADMIN_EMAIL)
//   --only DEMO-2026-0001,...     only these report IDs
//   --no-pdf                      create the cases but do not print reports
//   --out <folder>                where the PDFs go (default scripts/demo-data/output)
//   --totp-secret <base32>        automation only: compute the code instead of asking (or DEMO_ADMIN_TOTP_SECRET)
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { createInterface } from 'node:readline'
import { fileURLToPath } from 'node:url'
import { Api, ApiError } from './lib/api.mjs'
import { assertLocalProfile, assertLocalTarget } from './lib/guard.mjs'
import * as images from './lib/images.mjs'
import { CLIENTS, PRESETS, SCENARIOS } from './lib/scenarios.mjs'
import { totpCode } from './lib/totp.mjs'

const here = dirname(fileURLToPath(import.meta.url))

function parseArgs(argv) {
  const args = { url: 'http://localhost:8080', pdf: true, out: join(here, 'output') }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    const next = () => argv[++i]
    if (a === '--url') args.url = next()
    else if (a === '--email') args.email = next()
    else if (a === '--only') args.only = next().split(',').map((s) => s.trim()).filter(Boolean)
    else if (a === '--no-pdf') args.pdf = false
    else if (a === '--out') args.out = next()
    else if (a === '--totp-secret') args.totpSecret = next()
    else if (a === '--help' || a === '-h') args.help = true
    else throw new Error(`unknown option ${a} (try --help)`)
  }
  return args
}

function ask(question, { hidden = false } = {}) {
  return new Promise((resolve) => {
    const rl = createInterface({ input: process.stdin, output: process.stdout, terminal: true })
    if (hidden) {
      rl._writeToOutput = (text) => {
        if (text.startsWith(question)) rl.output.write(question)
      }
    }
    rl.question(question, (answer) => {
      rl.close()
      if (hidden) process.stdout.write('\n')
      resolve(answer.trim())
    })
  })
}

const log = (text = '') => console.log(text)
const step = (text) => console.log(`  - ${text}`)

async function main() {
  const args = parseArgs(process.argv.slice(2))
  if (args.help) {
    log(readHelp())
    return
  }
  const url = assertLocalTarget(args.url) // before anything is asked or sent
  log(`Demo data for the local stack at ${url.origin}`)

  const email = args.email ?? process.env.DEMO_ADMIN_EMAIL ?? (await ask('Administrator e-mail: '))
  const password = process.env.DEMO_ADMIN_PASSWORD ?? (await ask('Password: ', { hidden: true }))
  const secret = args.totpSecret ?? process.env.DEMO_ADMIN_TOTP_SECRET
  const code = secret ? totpCode(secret) : (process.env.DEMO_ADMIN_CODE ?? (await ask('Two-step code (6 digits): ')))

  const api = new Api(url.origin)
  await api.signIn(email, password, code)
  await assertLocalProfile(async () => {
    const response = await api.raw('GET', '/actuator')
    const links = response.ok ? ((await response.json().catch(() => null))?._links ?? {}) : {}
    return { status: response.status, endpoints: Object.keys(links).filter((name) => name !== 'self') }
  })
  log('Signed in. The backend is a local one.\n')

  const types = await api.get('/api/check-types')
  const clientIds = await ensureClients(api)
  const existing = await existingCases(api)

  const wanted = SCENARIOS.filter((s) => !args.only || args.only.includes(s.reportId))
  if (wanted.length === 0) throw new Error('no demo case matches --only')
  mkdirSync(args.out, { recursive: true })
  const summary = []

  for (const scenario of wanted) {
    log(`${scenario.reportId}  ${scenario.person.fullName}: ${scenario.note}`)
    if (existing.has(scenario.reportId)) {
      const caseId = existing.get(scenario.reportId)
      const printed = args.pdf && scenario.pdf && (await api.get(`/api/cases/${caseId}/reports`)).length > 0
      if (args.pdf && scenario.pdf && !printed) {
        step('already exists without a report: printing it')
        summary.push({ reportId: scenario.reportId, caseId, ...(await printReport(api, caseId, scenario.reportId, args.out)) })
      } else {
        step('already exists, skipped')
      }
      log()
      continue
    }
    const started = Date.now()
    const caseId = await createCase(api, scenario, clientIds[scenario.client], types)
    const entry = { reportId: scenario.reportId, caseId, candidate: scenario.person.fullName, checks: scenario.checks.length }
    if (args.pdf && scenario.pdf) {
      Object.assign(entry, await printReport(api, caseId, scenario.reportId, args.out))
    }
    entry.seconds = Math.round((Date.now() - started) / 100) / 10
    summary.push(entry)
    log()
  }

  writeFileSync(join(args.out, 'summary.json'), JSON.stringify(summary, null, 2))
  log('Done. Open http://localhost:5173 to look at the cases' + (args.pdf ? `, and ${args.out} for the PDFs.` : '.'))
}

function readHelp() {
  return 'Usage: node scripts/demo-data/seed-demo.mjs --email you@example.com [--url http://localhost:8080] [--only DEMO-2026-0001] [--no-pdf] [--out folder]\nSee the header of this file.'
}

async function ensureClients(api) {
  const list = await api.get('/api/clients')
  const ids = {}
  for (const wanted of CLIENTS) {
    const found = list.find((c) => c.name === wanted.name)
    if (found) {
      ids[wanted.key] = found.id
      continue
    }
    const created = await api.post('/api/clients', {
      name: wanted.name, displayName: wanted.displayName, defaultCheckTypes: wanted.defaultCheckTypes, active: true,
    })
    ids[wanted.key] = created.id
    step(`client created: ${wanted.name}`)
  }
  return ids
}

/** The demo cases that already exist: report ID -> case id. */
async function existingCases(api) {
  const page = await api.get('/api/cases?q=DEMO-&size=100&page=0')
  return new Map(page.items.map((c) => [c.reportId, c.id]))
}

/** The latest version of the case (every save needs it and every save changes it). */
async function fresh(api, caseId) {
  return api.get(`/api/cases/${caseId}`)
}

async function save(api, caseId, section, values) {
  const current = await fresh(api, caseId)
  return api.put(`/api/cases/${caseId}/${section}`, { version: current.version, ...values })
}

async function createCase(api, s, clientId, types) {
  const p = s.person
  const created = await api.post('/api/cases', { clientId, issueDate: '2026-06-11', dueDate: '2026-06-25' })
  const caseId = created.id
  const client = CLIENTS.find((c) => c.key === s.client)
  step(`case created (${created.reportId}), filling the sections`)

  await save(api, caseId, 'report-info', {
    reportId: s.reportId, issueDate: '2026-06-11', clientId, companyDisplayName: client.displayName, dueDate: '2026-06-25',
  })
  await save(api, caseId, 'candidate', {
    fullName: p.fullName, parentType: p.parentType, parentName: p.parentName, employeeId: p.employeeId, dob: p.dob, phone: p.phone,
    street: p.street, city: p.city, state: p.state, pin: p.pin, country: 'India',
  })
  if (!s.noPhoto) {
    const photo = images.photo(p)
    await api.upload(`/api/cases/${caseId}/candidate/photo`, photo.filename, photo.bytes, photo.mimeType)
    step('photo uploaded')
  }
  await save(api, caseId, 'verification-period', { show: s.period.show, start: s.period.start, end: s.period.end })
  const [title, subtitle] = PRESETS[s.overview.preset]
  await save(api, caseId, 'overview', {
    statusPreset: s.overview.preset, statusTitle: title, statusSubtitle: subtitle,
    totalOverride: null, completedOverride: null, overallStatusOverride: null,
  })
  await save(api, caseId, 'remarks', s.remarks)
  await save(api, caseId, 'settings', {
    layoutCards: s.settings.layoutCards, dateFormat: s.settings.dateFormat, watermarkEnabled: s.settings.watermarkEnabled,
    watermarkText: s.settings.watermarkText ?? 'NEXLYN VERIFIED',
  })

  const pictures = new Map() // the same picture is made once per case
  const picture = (design, variant) => {
    const key = `${design}/${variant ?? ''}`
    if (!pictures.has(key)) pictures.set(key, images.document(design, p, { variant }))
    return pictures.get(key)
  }

  for (const spec of s.checks) {
    await addCheck(api, caseId, spec, types, picture)
  }
  return caseId
}

async function addCheck(api, caseId, spec, types, picture) {
  const def = types.find((t) => t.code === spec.type)
  const added = await api.post(`/api/cases/${caseId}/checks`, { type: spec.type })
  const verified = spec.status === 'VERIFIED'
  const given = spec.fields ?? {}
  // An analyst ticks what was checked: only fields that have a value (typed here, or filled from the candidate).
  const tickOf = (f) => verified && (given[f.key] !== undefined || added.fields.find((x) => x.key === f.key)?.hasValue === true)

  // Same rules as the web form: typed values are sent, a field that follows the candidate says so.
  const fields = def.fields.map((f) => {
    const verifiedTick = tickOf(f)
    if (given[f.key] !== undefined) return { key: f.key, value: String(given[f.key]), verifiedTick }
    if (f.sensitive) return { key: f.key, verifiedTick }
    if (f.prefill) return { key: f.key, manual: false, verifiedTick }
    return { key: f.key, verifiedTick }
  })
  const details = (spec.details ?? []).length
    ? added.details.map((d) => {
        const over = spec.details.find((x) => x.label === d.label)
        return { label: d.label, value: over ? over.value : d.value }
      })
    : added.details.map((d) => ({ label: d.label, value: d.value }))
  const dates = spec.dates ?? {}
  const saved = await api.put(`/api/cases/${caseId}/checks/${added.id}`, {
    version: added.version,
    title: added.title,
    summaryDescription: added.summaryDescription,
    thisCardVerifies: added.thisCardVerifies,
    status: spec.status,
    verificationType: added.verificationType,
    requestedDate: 'requested' in dates ? dates.requested : added.requestedDate,
    completedDate: 'completed' in dates ? dates.completed : added.completedDate,
    remarks: spec.remarks ?? null,
    hasAttestation: spec.attestation ?? added.hasAttestation,
    barCouncilNo: added.barCouncilNo,
    disclaimer: added.disclaimer,
    fields,
    details,
  })
  step(`check ${spec.type} saved (${spec.status})`)

  for (const doc of spec.docs ?? []) {
    const file = picture(doc.design, doc.variant)
    const uploaded = await api.upload(`/api/checks/${saved.id}/documents?kind=CHECK_DOC`, file.filename, file.bytes, file.mimeType)
    if (doc.label || doc.moveToNextPage || doc.useLargerBox) {
      await api.put(`/api/documents/${uploaded.id}`, {
        label: doc.label ?? null, moveToNextPage: Boolean(doc.moveToNextPage), useLargerBox: Boolean(doc.useLargerBox), crop: null, version: uploaded.version,
      })
    }
    step(`  document: ${file.filename}`)
  }
  for (const block of spec.free ?? []) {
    if (block.kind === 'TEXT') {
      await api.post(`/api/cases/${caseId}/checks/${saved.id}/free-sections`, { kind: 'TEXT', text: block.text })
    } else {
      const file = picture(block.design)
      const uploaded = await api.upload(`/api/checks/${saved.id}/documents?kind=FREE_IMAGE`, file.filename, file.bytes, file.mimeType)
      await api.post(`/api/cases/${caseId}/checks/${saved.id}/free-sections`, { kind: 'IMAGE', documentId: uploaded.id })
    }
    step(`  ${block.kind === 'TEXT' ? 'text' : 'image'} block added`)
  }
}

async function printReport(api, caseId, reportId, outDir) {
  const problems = await api.get(`/api/cases/${caseId}/validation`)
  if (problems.errors.length) {
    step(`report blocked: ${problems.errors.map((e) => e.message).join('; ')}`)
    return { pdf: null, errors: problems.errors.map((e) => e.message) }
  }
  let job = await api.post(`/api/cases/${caseId}/reports`, { acknowledgeWarnings: true })
  const deadline = Date.now() + 180_000
  while (job.status === 'QUEUED' || job.status === 'RUNNING') {
    if (Date.now() > deadline) throw new Error(`report for ${reportId} took more than 3 minutes`)
    await new Promise((r) => setTimeout(r, 1500))
    job = await api.get(`/api/cases/${caseId}/reports/jobs/${job.id}`)
  }
  if (job.status !== 'DONE') {
    step(`report FAILED: ${job.error}`)
    return { pdf: null, error: job.error }
  }
  const bytes = await api.call('GET', `/api/cases/${caseId}/reports/${job.version}/download`, { as: 'bytes' })
  const file = join(outDir, `${reportId}-v${job.version}.pdf`)
  writeFileSync(file, bytes)
  const versions = await api.get(`/api/cases/${caseId}/reports`)
  const pages = versions.find((v) => v.version === job.version)?.pageCount
  step(`report printed: ${file} (${pages} pages, ${Math.round(bytes.length / 1024)} KB)`)
  for (const w of job.warnings ?? []) step(`  layout warning: ${w}`)
  return { pdf: file, pages, kilobytes: Math.round(bytes.length / 1024), warnings: job.warnings ?? [] }
}

main().catch((error) => {
  const cause = error?.cause ? ` (${error.cause.code ?? error.cause.message ?? error.cause})` : ''
  console.error(`\nStopped: ${error instanceof ApiError ? error.message : (error?.message ?? error)}${cause}`)
  process.exit(1)
})
