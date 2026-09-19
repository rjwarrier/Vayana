import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const speechMapping = bridge.slice(
    bridge.indexOf('function normalizeSpeechSegment'),
    bridge.indexOf('// The chapter\'s sentences'),
)
const context = vm.createContext({})
vm.runInContext(speechMapping, context)

test('normalized TTS word offsets map back through collapsed EPUB whitespace', () => {
    const normalized = context.normalizeSpeechSegment('  Hello \n world.  ')
    assert.equal(normalized.text, 'Hello world.')
    assert.deepEqual([...normalized.sourceRange(6, 11)], [10, 15])
})
