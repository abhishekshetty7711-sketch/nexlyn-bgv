// Run with:  node --test scripts/backup-keys/*.test.mjs
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { test } from 'node:test'
import { assertOutsideRepo, decryptKeys, encryptKeys, fingerprint, readKeys, REPO_ROOT } from './backup-keys.mjs'

const ENV = [
  '# comment', 'DB_PASSWORD=not-a-backed-up-key', 'PII_ENCRYPTION_KEY=oYM+FpNr77oUcKhEgqmaNsxg0ih3RVhbTjVycJOVz/A=',
  'TOTP_ENCRYPTION_KEY=EBf+GtsV+4WBj+ocXRHmP9QDn6I/rILEQjY+wbko1zc=', 'JWT_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----\\nABC==\\n-----END PRIVATE KEY-----', 'JWT_KEY_ID=x', '',
].join('\n')
const PASS = 'correct horse battery staple'

test('only the secret key lines are read, and a value keeps its = signs', () => {
  const keys = readKeys(ENV)
  assert.deepEqual(Object.keys(keys).sort(), ['JWT_PRIVATE_KEY', 'PII_ENCRYPTION_KEY', 'TOTP_ENCRYPTION_KEY'])
  assert.ok(keys.PII_ENCRYPTION_KEY.endsWith('/A='))
  assert.ok(!('DB_PASSWORD' in keys))
  assert.deepEqual(readKeys('PII_ENCRYPTION_KEY=\n'), {}, 'an empty value is not a key')
})

test('a backup opens with the passphrase and gives back the same keys', () => {
  const keys = readKeys(ENV)
  const file = encryptKeys(keys, PASS)
  const opened = decryptKeys(file, PASS)
  assert.deepEqual(opened.keys, keys)
  assert.equal(opened.fingerprints.PII_ENCRYPTION_KEY, fingerprint(keys.PII_ENCRYPTION_KEY))
})

test('the file does not contain the keys or the passphrase in readable form', () => {
  const file = encryptKeys(readKeys(ENV), PASS)
  for (const secret of [PASS, 'oYM+FpNr77oUcKhEgqmaNsxg0ih3RVhbTjVycJOVz', 'EBf+GtsV', 'BEGIN PRIVATE KEY']) {
    assert.ok(!file.includes(secret), secret)
    assert.ok(!Buffer.from(JSON.parse(file).data, 'base64').toString('latin1').includes(secret), secret + ' (decoded)')
  }
})

test('a wrong passphrase, a changed byte and a changed header are all refused', () => {
  const file = encryptKeys(readKeys(ENV), PASS)
  assert.throws(() => decryptKeys(file, 'a different passphrase'), /Wrong passphrase/)
  const tampered = JSON.parse(file)
  const data = Buffer.from(tampered.data, 'base64')
  data[0] ^= 1
  tampered.data = data.toString('base64')
  assert.throws(() => decryptKeys(JSON.stringify(tampered), PASS), /Wrong passphrase, or the file is damaged/)
  const relabelled = JSON.parse(file)
  relabelled.header.created = '1999-01-01'
  assert.throws(() => decryptKeys(JSON.stringify(relabelled), PASS), /damaged or was changed/)
  assert.throws(() => decryptKeys('not json', PASS), /not a Nexlyn key backup/)
})

test('a short passphrase and an empty key list are refused', () => {
  assert.throws(() => encryptKeys(readKeys(ENV), 'short'), /at least 14/)
  assert.throws(() => encryptKeys({}, PASS), /No keys found/)
})

test('two backups of the same keys differ (fresh salt and nonce)', () => {
  const keys = readKeys(ENV)
  assert.notEqual(encryptKeys(keys, PASS), encryptKeys(keys, PASS))
})

test('a backup inside the project folder is refused, one elsewhere is not', () => {
  assert.throws(() => assertOutsideRepo(join(REPO_ROOT, 'scripts')), /inside the project folder/)
  assert.throws(() => assertOutsideRepo(REPO_ROOT), /inside the project folder/)
  assert.doesNotThrow(() => assertOutsideRepo(tmpdir()))
})

test('the command line: backup, verify and restore work end to end and never print a key', () => {
  const dir = mkdtempSync(join(tmpdir(), 'nexlyn-keys-test-'))
  try {
    const env = join(dir, 'test.env')
    writeFileSync(env, ENV)
    const run = (...args) => execFileSync(process.execPath, [join(REPO_ROOT, 'scripts', 'backup-keys', 'backup-keys.mjs'), ...args],
      { env: { ...process.env, NEXLYN_BACKUP_PASSPHRASE: PASS }, encoding: 'utf8' })
    const out = run('backup', '--env', env, '--out', join(dir, 'usb'))
    assert.match(out, /Backup written and checked/)
    assert.match(out, new RegExp(fingerprint(readKeys(ENV).PII_ENCRYPTION_KEY)))
    assert.ok(!out.includes('oYM+FpNr77'), 'the key must not be printed')
    const file = out.match(/checked: (.+\.enc)/)[1].trim()
    const verified = run('verify', '--env', env, '--file', file)
    assert.match(verified, /SAME as the current \.env/)
    const restored = join(dir, 'recovered.env')
    run('restore', '--file', file, '--to', restored)
    assert.deepEqual(readKeys(readFileSync(restored, 'utf8')), readKeys(ENV))
    assert.throws(() => run('restore', '--file', file, '--to', restored), /already exists/)
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
})
