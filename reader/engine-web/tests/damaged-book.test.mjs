import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const bridge = readFileSync(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8').replaceAll('\r\n', '\n')
const start = bridge.indexOf('async function assertChaptersReadable')
const end = bridge.indexOf('/**\n * Where a popularity pill goes')
assert.ok(start > 0 && end > start, 'assertChaptersReadable not found in bridge.js')
const context = vm.createContext({})
vm.runInContext(bridge.slice(start, end), context)

const section = (load, linear = 'yes') => ({ linear, load })
const check = book => context.assertChaptersReadable(book)

await check({ sections: [] })
await check({ sections: [section(async () => 'blob:1')] })
// The cover can be missing while the text is fine.
await check({ sections: [section(async () => null), section(async () => 'blob:2')] })
// A throwing section does not hide a readable one.
await check({ sections: [section(async () => { throw new Error('x') }), section(async () => 'blob:3')] })
await assert.rejects(check({ sections: [section(async () => null), section(async () => null)] }), /damaged/)
// Non-linear sections are not part of the reading order and are not checked.
await check({ sections: [section(async () => null, 'no'), section(async () => 'blob:4')] })
console.log('damaged-book ok')
