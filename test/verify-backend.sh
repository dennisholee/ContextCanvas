#!/usr/bin/env bash
# Backend API Smoke Test — verifies MCP tools are accessible via REST
# Usage: ./test/verify-backend.sh [base_url]
#   base_url defaults to http://localhost:8080/api/mcp
set -euo pipefail

BASE="${1:-http://localhost:8080/api/mcp}"
PASS=0
FAIL=0

green() { printf "  \033[32m✓\033[0m %s\n" "$1"; }
red()   { printf "  \033[31m✗\033[0m %s\n" "$1"; }

echo "=========================================="
echo " ContextCanvas Backend API Smoke Test"
echo " Target: $BASE"
echo "=========================================="

# ── 1. List tools ──────────────────────────────────────────────────────────
echo ""
echo "--- Test: GET /tools ---"
TOOLS_RESP=$(curl -sf "$BASE/tools" 2>&1 || true)
if [ -z "$TOOLS_RESP" ]; then
  red "Failed to fetch tools — is the backend running?"
  FAIL=$((FAIL+1))
else
  TOOL_COUNT=$(echo "$TOOLS_RESP" | node -e "const d=require('fs').readFileSync('/dev/stdin','utf8'); const j=JSON.parse(d); console.log(j.length)")
  echo "  Tools returned: $TOOL_COUNT"
  echo "$TOOLS_RESP" | node -e "
    const d=require('fs').readFileSync('/dev/stdin','utf8');
    const j=JSON.parse(d);
    j.forEach(t => console.log('    - ' + t.name + ': ' + (t.description || '').slice(0, 60)));
  "
  if [ "$TOOL_COUNT" -ge 2 ]; then
    green "Got $TOOL_COUNT tools (expected >= 2)"
    PASS=$((PASS+1))
  else
    red "Expected >= 2 tools, got $TOOL_COUNT"
    FAIL=$((FAIL+1))
  fi
fi

# ── 2. Introspect schema ──────────────────────────────────────────────────
echo ""
echo "--- Test: POST /call introspect_schema ---"
SCHEMA_RESP=$(curl -sf -X POST "$BASE/call" \
  -H 'Content-Type: application/json' \
  -d '{"name":"introspect_schema","arguments":{}}' 2>&1 || true)
if [ -z "$SCHEMA_RESP" ]; then
  red "Failed to introspect schema"
  FAIL=$((FAIL+1))
else
  TABLE_COUNT=$(echo "$SCHEMA_RESP" | node -e "
    const d=require('fs').readFileSync('/dev/stdin','utf8');
    const j=JSON.parse(d);
    const tables = j.result?.tables || [];
    console.log(tables.length);
    tables.forEach(t => console.log('    - ' + t.tableName));
  ")
  echo "  Tables found: $TABLE_COUNT"
  if [ "$TABLE_COUNT" -ge 2 ]; then
    green "Schema introspection works ($TABLE_COUNT tables)"
    PASS=$((PASS+1))
  else
    red "Expected >= 2 tables, got $TABLE_COUNT"
    FAIL=$((FAIL+1))
  fi
fi

# ── 3. Query clients ──────────────────────────────────────────────────────
echo ""
echo "--- Test: POST /call query ---"
QUERY_RESP=$(curl -sf -X POST "$BASE/call" \
  -H 'Content-Type: application/json' \
  -d '{"name":"query","arguments":{"sql":"SELECT id, company_name, status FROM clients LIMIT 3"}}' 2>&1 || true)
if [ -z "$QUERY_RESP" ]; then
  red "Failed to execute query"
  FAIL=$((FAIL+1))
else
  ROW_COUNT=$(echo "$QUERY_RESP" | node -e "
    const d=require('fs').readFileSync('/dev/stdin','utf8');
    const j=JSON.parse(d);
    const rows = j.result?.rows || [];
    console.log(rows.length);
  ")
  echo "  Rows returned: $ROW_COUNT"
  if [ "$ROW_COUNT" -ge 1 ]; then
    green "Query execution works ($ROW_COUNT rows)"
    PASS=$((PASS+1))
  else
    red "Expected >= 1 row, got $ROW_COUNT"
    FAIL=$((FAIL+1))
  fi
fi

# ── Summary ────────────────────────────────────────────────────────────────
echo ""
echo "=========================================="
if [ "$FAIL" -eq 0 ]; then
  echo "  All $PASS tests passed! 🎉"
else
  echo "  $PASS passed, $FAIL failed"
  exit 1
fi
echo "=========================================="
