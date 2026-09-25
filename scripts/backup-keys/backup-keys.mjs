#!/usr/bin/env node
// Makes an ENCRYPTED, offline backup of the secret keys in an .env file (PII_ENCRYPTION_KEY, TOTP_ENCRYPTION_KEY,
// JWT_PRIVATE_KEY), checks that the backup can be opened again, and can restore it. No package needed, only Node.
//
//   node scripts/backup-keys/backup-keys.mjs backup  --out E:\            (E:\ = a USB stick, NOT this disk)
//   node scripts/backup-keys/backup-keys.mjs verify  --file E:\nexlyn-keys-2026-09-25.enc
//   node scripts/backup-keys/backup-keys.mjs restore --file E:\nexlyn-keys-2026-09-25.enc --to C:\safe\recovered.env
//
// Options: --env <file> the .env to read (default infra/local/.env; use infra/prod/.env on the server).
//
// You type the passphrase (hidden, at least 14 characters; a few random words is best). It is never stored: WITHOUT IT THE
// FILE CANNOT BE OPENED, so keep the passphrase apart from the file (in your head, in the password manager, on paper).
// The keys themselves are never printed: for each one the tool shows a short fingerprint (a piece of its hash) that you
// can note down to recognise the key later. The file uses scrypt (memory-hard) and AES-256-GCM (tampering is detected).
//
// It refuses to write the backup inside this project folder (git could pick it up) and warns when it is on the same
// disk as the .env: a backup next to the original does not survive that disk failing.
import { createCipheriv, createDecipheriv, createHash, randomBytes, scryptSync } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, parse, resolve, sep } from 'node:path'
import { createInterface } from 'node:readline'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
export const REPO_ROOT = resolve(here, '..', '..')
export const KEY_NAMES = ['PII_ENCRYPTION_KEY', 'TOTP_ENCRYPTION_KEY', 'JWT_PRIVATE_KEY']
export const MIN_PASSPHRASE = 14
const FORMAT = 'nexlyn-keys-v1'
const SCRYPT = { N: 131072, r: 8, p: 1 } // about 128 MB and a second of work per guess

/** The secret lines of an .env file: name -> value (the value is everything after the first "="). */
export function readKeys(envText) {
  const keys = {}
  for (const line of envText.split(/\r?\n/)) {
    const match = /^([A-Z0-9_]+)=(.*)$/.exec(line.trim())
    if (match && KEY_NAMES.includes(match[1]) && match[2] !== '') {
      keys[match[1]] = match[2]
    }
  }
  return keys
}

/** A short, harmless fingerprint of a secret: the first 12 hex characters of its SHA-256. */
export function fingerprint(value) {
  return createHash('sha256').update(value).digest('hex').slice(0, 12)
}

function derive(passphrase, salt, params) {
  return scryptSync(passphrase, salt, 32, { N: params.N, r: params.r, p: params.p, maxmem: 512 * 1024 * 1024 })
}

/** Encrypts the keys with the passphrase. Returns the text of the backup file. */
export function encryptKeys(keys, passphrase, now = new Date()) {
  if (Object.keys(keys).length === 0) {
    throw new Error('No keys found to back up (looked for ' + KEY_NAMES.join(', ') + ').')
  }
  if (passphrase.length < MIN_PASSPHRASE) {
    throw new Error(`The passphrase must have at least ${MIN_PASSPHRASE} characters.`)
  }
  const salt = randomBytes(16)
  const iv = randomBytes(12)
  const header = { format: FORMAT, created: now.toISOString().slice(0, 10), kdf: 'scrypt', ...SCRYPT, names: Object.keys(keys) }
  const cipher = createCipheriv('aes-256-gcm', derive(passphrase, salt, SCRYPT), iv)
  cipher.setAAD(Buffer.from(JSON.stringify(header)))
  const payload = JSON.stringify({ keys, fingerprints: Object.fromEntries(Object.entries(keys).map(([n, v]) => [n, fingerprint(v)])) })
  const data = Buffer.concat([cipher.update(payload, 'utf8'), cipher.final()])
  return JSON.stringify({ header, salt: salt.toString('base64'), iv: iv.toString('base64'), tag: cipher.getAuthTag().toString('base64'), data: data.toString('base64') }, null, 2) + '\n'
}

/** Decrypts a backup file. Throws a plain message for a wrong passphrase or a damaged file. */
export function decryptKeys(fileText, passphrase) {
  let file
  try {
    file = JSON.parse(fileText)
  } catch {
    throw new Error('This is not a Nexlyn key backup file.')
  }
  if (file?.header?.format !== FORMAT) {
    throw new Error('This is not a Nexlyn key backup file (or a newer version).')
  }
  try {
    const decipher = createDecipheriv('aes-256-gcm', derive(passphrase, Buffer.from(file.salt, 'base64'), file.header), Buffer.from(file.iv, 'base64'))
    decipher.setAAD(Buffer.from(JSON.stringify(file.header)))
    decipher.setAuthTag(Buffer.from(file.tag, 'base64'))
    const payload = Buffer.concat([decipher.update(Buffer.from(file.data, 'base64')), decipher.final()]).toString('utf8')
    return JSON.parse(payload)
  } catch {
    throw new Error('Wrong passphrase, or the file is damaged or was changed.')
  }
}

/** Throws if the backup would land inside the project folder. */
export function assertOutsideRepo(path, repoRoot = REPO_ROOT) {
  const target = resolve(path).toLowerCase()
  const root = resolve(repoRoot).toLowerCase()
  if (target === root || target.startsWith(root + sep)) {
    throw new Error(`Refusing to write the backup inside the project folder (${repoRoot}): git could pick it up. Choose a USB stick or another place.`)
  }
}

function ask(question, { hidden = false } = {}) {
  return new Promise((resolveAnswer) => {
    const rl = createInterface({ input: process.stdin, output: process.stdout, terminal: true })
    if (hidden) {
      rl._writeToOutput = (text) => {
        if (text.startsWith(question)) rl.output.write(question)
      }
    }
    rl.question(question, (answer) => {
      rl.close()
      if (hidden) process.stdout.write('\n')
      resolveAnswer(answer)
    })
  })
}

async function newPassphrase() {
  const fromEnv = process.env.NEXLYN_BACKUP_PASSPHRASE // for automation and tests only
  if (fromEnv) return fromEnv
  const first = await ask(`New passphrase (at least ${MIN_PASSPHRASE} characters, hidden): `, { hidden: true })
  const second = await ask('Type it again: ', { hidden: true })
  if (first !== second) throw new Error('The two passphrases are different. Nothing was written.')
  return first
}

const existingPassphrase = async () => process.env.NEXLYN_BACKUP_PASSPHRASE ?? ask('Passphrase (hidden): ', { hidden: true })

function parseArgs(argv) {
  const args = { command: argv[0], env: join(REPO_ROOT, 'infra', 'local', '.env') }
  for (let i = 1; i < argv.length; i++) {
    const next = () => argv[++i]
    if (argv[i] === '--out') args.out = next()
    else if (argv[i] === '--file') args.file = next()
    else if (argv[i] === '--to') args.to = next()
    else if (argv[i] === '--env') args.env = resolve(next())
    else throw new Error(`unknown option ${argv[i]}`)
  }
  return args
}

function showFingerprints(fingerprints) {
  for (const [name, print] of Object.entries(fingerprints)) console.log(`  ${name.padEnd(22)} fingerprint ${print}`)
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  if (args.command === 'backup') {
    if (!args.out) throw new Error('Say where to write it: --out E:\\  (a USB stick or other disk, not this project folder).')
    assertOutsideRepo(args.out)
    if (!existsSync(args.env)) throw new Error(`No such file: ${args.env}`)
    const keys = readKeys(readFileSync(args.env, 'utf8'))
    if (parse(resolve(args.out)).root.toLowerCase() === parse(resolve(args.env)).root.toLowerCase()) {
      console.log('WARNING: the backup goes to the same disk as the .env. If this disk fails or is lost, both are gone. Prefer a USB stick.\n')
    }
    const passphrase = await newPassphrase()
    const text = encryptKeys(keys, passphrase)
    mkdirSync(args.out, { recursive: true })
    const file = join(args.out, `nexlyn-keys-${new Date().toISOString().slice(0, 10)}.enc`)
    if (existsSync(file)) throw new Error(`${file} already exists: refusing to overwrite it.`)
    writeFileSync(file, text, { mode: 0o600 })
    // A backup that cannot be opened is worthless: open it again with the same passphrase and compare.
    const opened = decryptKeys(readFileSync(file, 'utf8'), passphrase)
    for (const [name, value] of Object.entries(keys)) {
      if (opened.keys[name] !== value) throw new Error(`Check failed for ${name}: the file was written but does not decrypt to the same key. Do not rely on it.`)
    }
    console.log(`Backup written and checked: ${file}`)
    console.log('It holds these keys (fingerprints only; note them down to recognise the keys later):')
    showFingerprints(opened.fingerprints)
    console.log('\nNext: copy the file to a SECOND place (a second USB stick or another person), and store the PASSPHRASE separately from the file.')
  } else if (args.command === 'verify') {
    if (!args.file) throw new Error('Say which file: --file E:\\nexlyn-keys-2026-09-25.enc')
    const opened = decryptKeys(readFileSync(args.file, 'utf8'), await existingPassphrase())
    console.log('The file opens with this passphrase. It holds:')
    showFingerprints(opened.fingerprints)
    if (existsSync(args.env)) {
      const current = readKeys(readFileSync(args.env, 'utf8'))
      for (const [name, value] of Object.entries(opened.keys)) {
        const state = current[name] === undefined ? 'not in the current .env' : current[name] === value ? 'SAME as the current .env' : 'DIFFERENT from the current .env'
        console.log(`  ${name.padEnd(22)} ${state}`)
      }
    }
  } else if (args.command === 'restore') {
    if (!args.file || !args.to) throw new Error('Usage: restore --file <backup> --to <new .env-style file>')
    if (existsSync(args.to)) throw new Error(`${args.to} already exists: refusing to overwrite it. Choose a new file name.`)
    assertOutsideRepo(args.to)
    const opened = decryptKeys(readFileSync(args.file, 'utf8'), await existingPassphrase())
    writeFileSync(args.to, Object.entries(opened.keys).map(([n, v]) => `${n}=${v}`).join('\n') + '\n', { mode: 0o600 })
    console.log(`Restored ${Object.keys(opened.keys).length} key(s) into ${args.to}. Copy the lines you need into the .env, then delete that file.`)
  } else {
    console.log('Usage: backup --out <folder> | verify --file <backup> | restore --file <backup> --to <newfile>   [--env <.env>]\nSee the header of this file.')
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(`\nStopped: ${error?.message ?? error}`)
    process.exit(1)
  })
}
