// Makes the FAKE photos and documents for the demo cases, by drawing small HTML pages and letting the
// computer's Chrome / Edge / Chromium take a picture (or print a PDF) of them. Nothing is downloaded and no
// package is needed. Every picture is stamped SPECIMEN / FAKE DEMO DATA.
import { execFileSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'

const CANDIDATES = [
  process.env.CHROME_PATH,
  process.env.CHROMIUM_PATH,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/usr/bin/chromium-browser',
].filter(Boolean)

export function findBrowser() {
  const found = CANDIDATES.find((path) => existsSync(path))
  if (!found) {
    throw new Error('No Chrome, Edge or Chromium found. Install one, or set CHROME_PATH to its program file.')
  }
  return found
}

const scratch = mkdtempSync(join(tmpdir(), 'nexlyn-demo-'))
process.on('exit', () => rmSync(scratch, { recursive: true, force: true }))

let counter = 0

function run(html, args, outFile) {
  const id = ++counter
  const source = join(scratch, `page-${id}.html`)
  writeFileSync(source, html)
  execFileSync(
    findBrowser(),
    ['--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars', `--user-data-dir=${join(scratch, `profile-${id}`)}`,
      ...args(outFile), pathToFileURL(source).href],
    { stdio: 'ignore', timeout: 60_000 },
  )
  return readFileSync(outFile)
}

function png(html, width, height) {
  const out = join(scratch, `out-${++counter}.png`)
  return run(html, (o) => [`--window-size=${width},${height}`, '--force-device-scale-factor=1', `--screenshot=${o}`], out)
}

function pdf(html) {
  const out = join(scratch, `out-${++counter}.pdf`)
  return run(html.replace('</style>', '@page{size:A4;margin:0}</style>'), (o) => ['--no-pdf-header-footer', `--print-to-pdf=${o}`], out)
}

const esc = (text) => String(text).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c])

function page(width, height, body, css = '') {
  return `<!doctype html><html><head><meta charset="utf-8"><style>
*{box-sizing:border-box}html,body{margin:0;padding:0}
body{width:${width}px;height:${height}px;position:relative;overflow:hidden;background:#fff;font-family:Arial,"Segoe UI",sans-serif;color:#1f2937}
.stamp{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;text-align:center;transform:rotate(-24deg);
font-size:${Math.round(width / 15)}px;font-weight:800;letter-spacing:3px;color:rgba(190,0,0,.14);line-height:1.15;pointer-events:none}
${css}</style></head><body>${body}<div class="stamp">SPECIMEN<br>FAKE DEMO DATA</div></body></html>`
}

const groups = (digits) => digits.replace(/(\d{4})(?=\d)/g, '$1 ')

/** A cartoon head and shoulders on a plain background, marked as a demo photo. */
export function photo(person) {
  const initials = person.fullName.split(/\s+/).map((w) => w[0]).join('').slice(0, 2)
  const skin = person.skin ?? '#d9a87c'
  const body = `<svg viewBox="0 0 600 760" width="600" height="760" xmlns="http://www.w3.org/2000/svg">
  <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${person.backdrop ?? '#cfe0f2'}"/><stop offset="1" stop-color="#eef3f9"/></linearGradient></defs>
  <rect width="600" height="760" fill="url(#g)"/>
  <path d="M60 760 C60 600 170 540 300 540 C430 540 540 600 540 760 Z" fill="${person.shirt ?? '#3b5b8c'}"/>
  <rect x="255" y="470" width="90" height="90" rx="30" fill="${skin}"/>
  <ellipse cx="300" cy="330" rx="130" ry="160" fill="${skin}"/>
  <path d="M168 320 C170 190 260 150 300 150 C350 150 432 190 432 320 C420 250 380 215 300 215 C230 215 180 250 168 320 Z" fill="#2b2118"/>
  <circle cx="255" cy="335" r="11" fill="#2b2118"/><circle cx="345" cy="335" r="11" fill="#2b2118"/>
  <path d="M255 410 Q300 440 345 410" stroke="#7a3b2e" stroke-width="8" fill="none" stroke-linecap="round"/>
  <text x="300" y="740" text-anchor="middle" font-family="Arial" font-size="28" fill="#fff" font-weight="700">DEMO PHOTO ${esc(initials)}</text></svg>`
  return { filename: `${person.slug}-photo.png`, mimeType: 'image/png', bytes: png(page(600, 760, body), 600, 760) }
}

function aadhaarCard(p) {
  const body = `<div style="position:absolute;inset:0;border:6px solid #b91c1c">
  <div style="height:96px;background:linear-gradient(90deg,#f59e0b,#fff 50%,#16a34a);display:flex;align-items:center;padding:0 32px;font-size:30px;font-weight:700;color:#7c2d12">
  Identity Card - specimen layout</div>
  <div style="display:flex;padding:28px 32px;gap:32px">
   <div style="width:210px;height:260px;background:#cfe0f2;border:2px solid #94a3b8;display:flex;align-items:center;justify-content:center;font-weight:700;color:#475569">PHOTO</div>
   <div style="font-size:26px;line-height:1.5"><b style="font-size:32px">${esc(p.fullName)}</b><br>DOB: ${esc(p.dobText)}<br>${esc(p.gender)}<br>
   <span style="font-size:20px;color:#475569">${esc(p.street)}, ${esc(p.city)}, ${esc(p.state)} - ${esc(p.pin)}</span></div></div>
  <div style="position:absolute;left:0;right:0;bottom:36px;text-align:center;font-size:54px;letter-spacing:4px;font-weight:700">${groups(p.aadhaar)}</div></div>`
  return { filename: `${p.slug}-aadhaar.png`, mimeType: 'image/png', bytes: png(page(1011, 638, body), 1011, 638) }
}

function panCard(p) {
  const body = `<div style="position:absolute;inset:0;border:6px solid #1d4ed8;background:#eef4ff">
  <div style="height:90px;background:#1d4ed8;color:#fff;display:flex;align-items:center;padding:0 32px;font-size:28px;font-weight:700">Permanent Account Number Card - specimen layout</div>
  <div style="display:flex;padding:26px 32px;gap:32px"><div style="font-size:26px;line-height:1.7">
   <span style="font-size:18px;color:#475569">Permanent Account Number</span><br><b style="font-size:46px;letter-spacing:5px">${esc(p.pan)}</b><br>
   <span style="font-size:18px;color:#475569">Name</span><br><b>${esc(p.fullName.toUpperCase())}</b><br>
   <span style="font-size:18px;color:#475569">Father's Name</span><br><b>${esc(p.parentName.toUpperCase())}</b><br>
   <span style="font-size:18px;color:#475569">Date of Birth</span> <b>${esc(p.dobText)}</b></div>
   <div style="margin-left:auto;width:190px;height:240px;background:#cfe0f2;border:2px solid #94a3b8;display:flex;align-items:center;justify-content:center;font-weight:700;color:#475569">PHOTO</div></div></div>`
  return { filename: `${p.slug}-pan.png`, mimeType: 'image/png', bytes: png(page(1011, 638, body), 1011, 638) }
}

/** An A4 page (portrait) with a letterhead and a few paragraphs. */
function letterHtml(width, height, head, title, paragraphs, foot) {
  const paras = paragraphs.map((t) => `<p style="margin:0 0 ${Math.round(width / 45)}px">${t}</p>`).join('')
  return page(width, height, `<div style="padding:${Math.round(width / 12)}px">
  <div style="border-bottom:${Math.round(width / 300)}px solid #1e3a8a;padding-bottom:${Math.round(width / 60)}px;margin-bottom:${Math.round(width / 25)}px">
   <div style="font-size:${Math.round(width / 28)}px;font-weight:800;color:#1e3a8a">${esc(head)}</div>
   <div style="font-size:${Math.round(width / 60)}px;color:#64748b">12, Demo Business Park, Sample Road, Bengaluru 560001 (fictitious address)</div></div>
  <div style="font-size:${Math.round(width / 34)}px;font-weight:700;margin-bottom:${Math.round(width / 30)}px;text-align:center;text-decoration:underline">${esc(title)}</div>
  <div style="font-size:${Math.round(width / 52)}px;line-height:1.6">${paras}</div>
  <div style="position:absolute;left:${Math.round(width / 12)}px;bottom:${Math.round(width / 10)}px;font-size:${Math.round(width / 58)}px;color:#475569">${foot}</div></div>`)
}

const A4 = { w: 1654, h: 2339 } // A4 at 200 dpi: the long side is over 1500 px, so it counts as high quality

const LETTERS = {
  court: (p, v) => ({
    head: 'District Court Records Office (specimen)',
    title: 'COURT RECORD SEARCH REPORT',
    paragraphs: [
      `Name searched: <b>${esc(p.fullName)}</b>, S/o or D/o ${esc(p.parentName)}, ${esc(p.street)}, ${esc(p.city)} - ${esc(p.pin)}.`,
      'Databases searched: civil and criminal case records of the district and sessions courts, and the eCourts public service (fictitious search).',
      v === 'record'
        ? '<b>Result:</b> ONE civil matter was found: a small money-recovery suit filed in 2021 against a person of a similar name; details in the annexure. The identity match with the candidate is <b>not confirmed</b>.'
        : '<b>Result:</b> NO criminal or civil record was found against the person named above for the period searched.',
      'Search period: last 7 years. This is a specimen document made for testing; it is not a real court record.',
    ],
    foot: 'Authorised signatory (fictitious) - Ref DEMO/CT/0001',
  }),
  employment: (p) => ({
    head: `${p.employer} (fictitious)`,
    title: 'EXPERIENCE LETTER',
    paragraphs: [
      `This is to certify that <b>${esc(p.fullName)}</b> (Employee ID ${esc(p.employeeIdPrev ?? 'E-4471')}) worked with ${esc(p.employer)} as <b>${esc(p.designation)}</b> from ${esc(p.joined)} to ${esc(p.left)}.`,
      'During this period the conduct and performance of the employee were found satisfactory. We wish them success in their future work.',
      'This letter is a specimen made for testing and does not refer to a real person or company.',
    ],
    foot: 'HR Department (fictitious) - Ref DEMO/EMP/0001',
  }),
  address: (p) => ({
    head: 'City Utilities Board (specimen)',
    title: 'ELECTRICITY BILL - ADDRESS PROOF',
    paragraphs: [
      `Consumer: <b>${esc(p.fullName)}</b><br>Service address: ${esc(p.street)}, ${esc(p.city)}, ${esc(p.state)} - ${esc(p.pin)}`,
      'Billing period: one month. Amount due: 1,240.00 (specimen). Meter number DEMO-000123.',
      'This bill is a specimen made for testing.',
    ],
    foot: 'Customer care (fictitious)',
  }),
  credit: (p) => ({
    head: 'Sample Credit Bureau (fictitious)',
    title: 'CREDIT INFORMATION REPORT (SPECIMEN)',
    paragraphs: [
      `Name: <b>${esc(p.fullName)}</b>. Score: <b>${p.cibil ?? 782}</b> (range 300 to 900). Report date: ${esc(p.reportDate ?? '18 June 2026')}.`,
      'Accounts: 2 open (a credit card and a two-wheeler loan), 0 overdue, 0 written off, 0 defaults.',
      'This report is a specimen made for testing.',
    ],
    foot: 'Sample Credit Bureau - not a real bureau report',
  }),
  uan: (p) => ({
    head: 'Provident Fund Passbook (specimen layout)',
    title: 'MEMBER PASSBOOK',
    paragraphs: [
      `Member: <b>${esc(p.fullName)}</b>. UAN: <b>${esc(p.uan)}</b>.`,
      `Establishment: ${esc(p.employer)} (fictitious). Date of joining: ${esc(p.joined)}. Date of exit: ${esc(p.left)}.`,
      'Contribution entries are omitted. This passbook is a specimen made for testing.',
    ],
    foot: 'Specimen only',
  }),
}

function letter(kind, p, variant) {
  const spec = LETTERS[kind](p, variant)
  return letterHtml(A4.w, A4.h, spec.head, spec.title, spec.paragraphs, spec.foot)
}

function degree(p) {
  const body = `<div style="position:absolute;inset:36px;border:10px double #92400e;text-align:center;padding:90px 120px">
  <div style="font-size:64px;font-weight:800;color:#92400e">Sample University (fictitious)</div>
  <div style="font-size:34px;margin-top:40px">This is to certify that</div>
  <div style="font-size:70px;font-weight:700;margin:40px 0">${esc(p.fullName)}</div>
  <div style="font-size:34px;line-height:1.7">has been awarded the degree of <b>${esc(p.degree)}</b> in <b>${esc(p.specialization)}</b><br>in the year ${esc(p.yearOfPassing)} with ${esc(p.grade)}</div>
  <div style="font-size:26px;margin-top:80px;color:#64748b">Roll number ${esc(p.roll)} - specimen certificate made for testing</div></div>`
  return page(1754, 1240, body)
}

/**
 * The fake supporting documents. `kind` names the design; `person` holds the fake facts to print on it.
 * Returns { filename, mimeType, bytes }.
 */
export function document(kind, person, options = {}) {
  const slug = person.slug
  switch (kind) {
    case 'aadhaar':
      return aadhaarCard(person)
    case 'pan':
      return panCard(person)
    case 'court':
      return { filename: `${slug}-court-search.png`, mimeType: 'image/png', bytes: png(letter('court', person, options.variant), A4.w, A4.h) }
    case 'employment':
      return { filename: `${slug}-experience-letter.png`, mimeType: 'image/png', bytes: png(letter('employment', person), A4.w, A4.h) }
    case 'employment-pdf': {
      // a real A4 page (794 x 1123 CSS pixels), so the PDF is one page and nothing is cut off
      const spec = LETTERS.employment(person)
      return { filename: `${slug}-experience-letter.pdf`, mimeType: 'application/pdf', bytes: pdf(letterHtml(794, 1123, spec.head, spec.title, spec.paragraphs, spec.foot)) }
    }
    case 'degree':
      return { filename: `${slug}-degree.png`, mimeType: 'image/png', bytes: png(degree(person), 1754, 1240) }
    case 'address':
      // deliberately small (a phone snapshot): shows the "Low" quality badge
      return { filename: `${slug}-address-proof.png`, mimeType: 'image/png', bytes: png(letterHtml(720, 1000, 'City Utilities Board (specimen)', 'ELECTRICITY BILL - ADDRESS PROOF', LETTERS.address(person).paragraphs, 'Customer care (fictitious)'), 720, 1000) }
    case 'credit':
      return { filename: `${slug}-credit-report.png`, mimeType: 'image/png', bytes: png(letter('credit', person), A4.w, A4.h) }
    case 'uan':
      return { filename: `${slug}-uan-passbook.png`, mimeType: 'image/png', bytes: png(letter('uan', person), A4.w, A4.h) }
    case 'sketch':
      return {
        filename: `${slug}-site-sketch.png`,
        mimeType: 'image/png',
        bytes: png(page(1200, 800, `<div style="padding:60px;font-size:36px"><b>Field visit sketch (specimen)</b><br><br>House 14 - main road - landmark: demo temple<br>
        <div style="margin-top:50px;border:4px dashed #64748b;height:380px;display:flex;align-items:center;justify-content:center;font-size:44px">[ sketch of the street ]</div></div>`), 1200, 800),
      }
    default:
      throw new Error(`unknown document design "${kind}"`)
  }
}
