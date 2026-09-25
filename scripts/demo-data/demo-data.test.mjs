// Run with:  node --test scripts/demo-data/*.test.mjs
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { assertLocalProfile, assertLocalTarget } from './lib/guard.mjs'
import { SCENARIOS } from './lib/scenarios.mjs'
import { totpCode } from './lib/totp.mjs'
import { checkDigit, fakeAadhaar, isValid } from './lib/verhoeff.mjs'

test('the guard accepts only plain http on this computer', () => {
  for (const ok of ['http://localhost:8080', 'http://127.0.0.1:8080', 'http://localhost', 'http://[::1]:8080']) {
    assert.doesNotThrow(() => assertLocalTarget(ok), ok)
  }
  for (const bad of [
    'https://nexlyn.example.com',
    'http://nexlyn.example.com',
    'http://10.0.0.5:8080',
    'http://192.168.1.20:8080',
    'https://localhost:8443',
    'http://localhost.evil.com',
    'http://localhost@evil.com',
    'http://evil.com/localhost',
    'not a url',
    '',
  ]) {
    assert.throws(() => assertLocalTarget(bad), undefined, bad)
  }
})

test('the guard refuses to write unless the backend lists the local-only endpoints', async () => {
  await assertLocalProfile(async () => ({ status: 200, endpoints: ['health', 'info'] }))
  // what the production profile shows (application-prod.yml exposes health only)
  await assert.rejects(assertLocalProfile(async () => ({ status: 200, endpoints: ['health'] })), /may be a real installation/)
  await assert.rejects(assertLocalProfile(async () => ({ status: 200, endpoints: [] })), /may be a real installation/)
  for (const status of [401, 403, 404, 500]) {
    await assert.rejects(assertLocalProfile(async () => ({ status, endpoints: ['health', 'info'] })), /Refusing to write/, String(status))
  }
})

test('the script checks the address before asking for anything, and the profile before its first write', () => {
  const source = readFileSync(new URL('./seed-demo.mjs', import.meta.url), 'utf8')
  const at = (needle) => {
    const index = source.indexOf(needle)
    assert.ok(index >= 0, `missing ${needle}`)
    return index
  }
  assert.ok(at('assertLocalTarget(args.url)') < at("ask('Administrator e-mail"), 'address check comes before the first question')
  assert.ok(at('assertLocalTarget(args.url)') < at('api.signIn('), 'address check comes before sign-in')
  assert.ok(at('await assertLocalProfile(') < at('await ensureClients(api)'), 'profile check comes before the first write')
})

test('Verhoeff: known example and round trips', () => {
  assert.equal(checkDigit('236'), 3) // the worked example of the algorithm: 2363 is valid
  assert.ok(isValid('2363'))
  assert.ok(!isValid('2364'))
  const number = fakeAadhaar('23456789012')
  assert.equal(number.length, 12)
  assert.ok(isValid(number))
})

test('one-time codes match the RFC 6238 test vector', () => {
  // secret "12345678901234567890" in base32, time 59 s: 94287082 with 8 digits, so 287082 with 6
  assert.equal(totpCode('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', 59_000), '287082')
})

test('every demo number is fake but in the accepted format, and report IDs are unique DEMO- ids', () => {
  const ids = new Set()
  for (const s of SCENARIOS) {
    assert.match(s.reportId, /^DEMO-\d{4}-\d{4}$/)
    assert.ok(!ids.has(s.reportId))
    ids.add(s.reportId)
    const p = s.person
    assert.match(p.aadhaar, /^[2-9]\d{11}$/, p.slug)
    assert.ok(isValid(p.aadhaar), p.slug)
    assert.match(p.pan, /^ZZZP[A-Z]000\d Z?$|^ZZZP[A-Z]000\dZ$/, p.slug) // patterned and obviously fake
    assert.match(p.uan, /^\d{12}$/, p.slug)
    assert.match(p.phone, /^98765432\d\d$/, p.slug)
  }
})
