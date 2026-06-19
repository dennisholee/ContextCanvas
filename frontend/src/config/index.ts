export const config = {
  MCP_API_URL: import.meta.env.VITE_MCP_API_URL ?? 'http://localhost:8080/api/mcp',
  POSTHOG_KEY: import.meta.env.VITE_POSTHOG_KEY ?? '',
  TEST_MODE:   import.meta.env.VITE_TEST_MODE    === 'true',
} as const
