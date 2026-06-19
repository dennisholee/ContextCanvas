/**
 * Standalone verification of useLLM.ts core parsing functions.
 * Run: node test/verify-parsers.mjs
 *
 * Tests:
 * 1. parseTextFunctionCalls — parsing <function> blocks from models without native tool_calls
 * 2. parseA2uiPayload — parsing ---A2UI_START--- / ---A2UI_END--- markers
 */

// ── Functions under test (copied from useLLM.ts) ────────────────────────────

function parseA2uiPayload(content) {
  const startMarker = '---A2UI_START---'
  const endMarker = '---A2UI_END---'
  const startIdx = content.indexOf(startMarker)
  const endIdx = content.indexOf(endMarker)

  if (startIdx === -1 || endIdx === -1) {
    return { text: content }
  }

  const text = content.slice(0, startIdx).trim()
  const jsonStr = content.slice(startIdx + startMarker.length, endIdx).trim()

  try {
    const payload = JSON.parse(jsonStr)
    return { text, payload }
  } catch {
    return { text: content }
  }
}

function parseTextFunctionCalls(content) {
  const calls = []
  const funcRegex = /<function>(\w+)<\/function>\s*\n?\s*(\{[\s\S]*?\})/g
  let match

  while ((match = funcRegex.exec(content)) !== null) {
    const name = match[1]
    const jsonStr = match[2]
    try {
      const args = JSON.parse(jsonStr)
      calls.push({ name, arguments: args, snippet: match[0] })
    } catch {
      // Invalid JSON — skip
    }
  }

  return calls
}

// ── Tests ───────────────────────────────────────────────────────────────────
let passed = 0
let failed = 0

function test(name, fn) {
  try {
    fn()
    passed++
    console.log(`  ✓ ${name}`)
  } catch (e) {
    failed++
    console.log(`  ✗ ${name}: ${e.message}`)
  }
}

function assert(condition, msg) {
  if (!condition) throw new Error(msg || 'Assertion failed')
}

console.log('\n=== parseA2uiPayload tests ===\n')

test('returns text-only when no markers present', () => {
  const result = parseA2uiPayload('Hello world')
  assert(result.text === 'Hello world', 'text should match')
  assert(result.payload === undefined, 'no payload')
})

test('parses line_chart payload', () => {
  const input = 'Here is the trend data.\n\n---A2UI_START---\n{"componentType":"line_chart","title":"Sales","data":[{"month":"Jan","revenue":100}]}\n---A2UI_END---'
  const result = parseA2uiPayload(input)
  assert(result.text === 'Here is the trend data.', `text mismatch: "${result.text}"`)
  assert(result.payload.componentType === 'line_chart', 'componentType should be line_chart')
  assert(result.payload.title === 'Sales', 'title should be Sales')
  assert(result.payload.data.length === 1, 'should have 1 data point')
  assert(result.payload.data[0].month === 'Jan', 'first month should be Jan')
})

test('parses metric_card payload', () => {
  const input = 'Summary:\n---A2UI_START---\n{"componentType":"metric_card","title":"Revenue","data":{"value":450000,"unit":"USD"}}\n---A2UI_END---'
  const result = parseA2uiPayload(input)
  assert(result.text === 'Summary:', 'text should be "Summary:"')
  assert(result.payload.componentType === 'metric_card')
  assert(result.payload.data.value === 450000)
})

test('parses dashboard composite payload', () => {
  const input = 'Overview:\n---A2UI_START---\n{"componentType":"dashboard","layout":"grid","components":[{"componentType":"metric_card","title":"Revenue","data":{"value":100}}]}\n---A2UI_END---'
  const result = parseA2uiPayload(input)
  assert(result.payload.componentType === 'dashboard')
  assert(result.payload.layout === 'grid')
  assert(result.payload.components.length === 1)
  assert(result.payload.components[0].componentType === 'metric_card')
})

test('returns full text on malformed JSON', () => {
  const input = 'Some text\n---A2UI_START---\n{invalid json}\n---A2UI_END---'
  const result = parseA2uiPayload(input)
  assert(result.text === input, 'should return original text on parse failure')
  assert(result.payload === undefined, 'no payload on malformed JSON')
})

console.log('\n=== parseTextFunctionCalls tests ===\n')

test('parses single function call', () => {
  const input = '<function>query</function>\n{"sql": "SELECT * FROM clients LIMIT 5"}'
  const calls = parseTextFunctionCalls(input)
  assert(calls.length === 1, 'should find 1 call')
  assert(calls[0].name === 'query', 'name should be query')
  assert(calls[0].arguments.sql === 'SELECT * FROM clients LIMIT 5')
})

test('parses function call with params array', () => {
  const input = 'Let me query the database.\n<function>query</function>\n{"sql": "SELECT * FROM sales WHERE id = ?", "params": ["42"]}'
  const calls = parseTextFunctionCalls(input)
  assert(calls.length === 1, 'should find 1 call')
  assert(calls[0].name === 'query')
  assert(calls[0].arguments.params[0] === '42')
})

test('parses multiple function calls', () => {
  const input = '<function>introspect_schema</function>\n{}\n<function>query</function>\n{"sql": "SELECT 1"}'
  const calls = parseTextFunctionCalls(input)
  assert(calls.length === 2, 'should find 2 calls')
  assert(calls[0].name === 'introspect_schema')
  assert(calls[1].name === 'query')
})

test('returns empty array when no function calls', () => {
  const calls = parseTextFunctionCalls('Just some random text without functions')
  assert(calls.length === 0, 'should find 0 calls')
})

test('returns empty array on malformed function JSON', () => {
  const input = '<function>query</function>\n{invalid}'
  const calls = parseTextFunctionCalls(input)
  assert(calls.length === 0, 'should skip malformed JSON')
})

test('handles resolve_entity function', () => {
  const input = '<function>resolve_entity</function>\n{"name": "Acme", "table": "clients"}'
  const calls = parseTextFunctionCalls(input)
  assert(calls.length === 1, 'should find 1 call')
  assert(calls[0].name === 'resolve_entity')
  assert(calls[0].arguments.name === 'Acme')
  assert(calls[0].arguments.table === 'clients')
})

// ── Summary ─────────────────────────────────────────────────────────────────
console.log(`\n=== Results: ${passed} passed, ${failed} failed ===\n`)
process.exit(failed > 0 ? 1 : 0)
