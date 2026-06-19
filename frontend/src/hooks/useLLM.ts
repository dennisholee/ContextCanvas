import type { A2UIResponse } from '../components/a2ui/A2UITypes'
import { config } from '../config'
import { trackEvent } from '../services/analytics'

interface LLMMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
  a2uiPayload?: A2UIResponse
}

interface ChatHistoryMessage {
  role: string
  content: string
}

/**
 * Hook for communicating with the LLM via the backend chat service.
 * The backend (LlmChatService) handles the entire function-calling loop with the LLM
 * and calls MCP tools via ToolDispatcher in-process.
 *
 * Architecture: Frontend → Backend /api/chat → LLM Provider (via LlmChatService)
 *               ↓
 *          ToolDispatcher (in-process MCP dispatch)
 */
export function useLLM() {
  const sendMessage = async (
    message: string,
    history: LLMMessage[],
  ): Promise<LLMMessage> => {
    trackEvent('message_sent', { messageLength: message.length })

    // Test mode — simulate LLM response
    if (config.TEST_MODE) {
      return simulateResponse(message)
    }

    const backendUrl = config.MCP_API_URL?.replace('/api/mcp', '/api/chat') ?? '/api/chat'

    try {
      const historyPayload: ChatHistoryMessage[] = history.map(({ role, content }) => ({
        role,
        content,
      }))

      const response = await fetch(backendUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message,
          history: historyPayload,
        }),
      })

      if (!response.ok) {
        const errorText = await response.text().catch(() => '')
        throw new Error(`Backend chat failed: ${response.status}${errorText ? ` — ${errorText.slice(0, 200)}` : ''}`)
      }

      const data = await response.json()
      const text: string = data.text ?? ''
      const a2uiJson: string = data.a2uiJson ?? ''

      // Parse A2UI JSON if present
      let a2uiPayload: A2UIResponse | undefined
      if (a2uiJson) {
        try {
          a2uiPayload = JSON.parse(a2uiJson) as A2UIResponse
        } catch (e) {
          console.warn('[useLLM] Failed to parse A2UI JSON:', e)
        }
      }

      return {
        role: 'assistant',
        content: text,
        a2uiPayload,
      }
    } catch (error) {
      console.error('[useLLM] Backend chat call failed:', error)
      return {
        role: 'assistant',
        content: '⚠️ The backend is not running. Please start it with: `cd backend && ./mvnw spring-boot:run`',
      }
    }
  }

  return { sendMessage }
}

/**
 * Simulates LLM responses for E2E testing without a real API key.
 */
function simulateResponse(userMessage: string): LLMMessage {
  const lower = userMessage.toLowerCase()

  if (lower.includes('add') || lower.includes('create') || lower.includes('new')) {
    return {
      role: 'assistant',
      content: 'I found the relevant record. Please review and confirm:',
      a2uiPayload: {
        componentType: 'confirmation_card',
        title: 'Confirm Transaction',
        action: { type: 'create', entity: 'sales', newValues: { client_id: 42, deal_amount: 5000, stage: 'pipeline' } },
      },
    }
  }

  if (lower.includes('trend') || lower.includes('over time') || lower.includes('month')) {
    return {
      role: 'assistant',
      content: "Here's the trend data:",
      a2uiPayload: {
        componentType: 'line_chart',
        title: 'Sales Trend',
        data: [
          { month: 'Jul', revenue: 45000 },
          { month: 'Aug', revenue: 52000 },
          { month: 'Sep', revenue: 48000 },
        ],
      },
    }
  }

  if (lower.includes('summary') || lower.includes('total') || lower.includes('kpi')) {
    return {
      role: 'assistant',
      content: "Here's the summary:",
      a2uiPayload: { componentType: 'metric_card', title: 'Total Revenue', data: { value: 450000, unit: 'USD' } },
    }
  }

  if (lower.includes('list') || lower.includes('show me') || lower.includes('all')) {
    return {
      role: 'assistant',
      content: 'Here are the results:',
      a2uiPayload: {
        componentType: 'data_table',
        title: 'Clients',
        data: [
          { id: 1, name: 'Acme Corp', status: 'active' },
          { id: 2, name: 'BetterCloud', status: 'lead' },
        ],
      },
    }
  }

  if (lower.includes('dashboard') || lower.includes('overview') || lower.includes('full picture')) {
    return {
      role: 'assistant',
      content: "Here's the full overview:",
      a2uiPayload: {
        componentType: 'dashboard',
        layout: 'grid',
        components: [
          { componentType: 'metric_card', title: 'Total Revenue', data: { value: 450000, unit: 'USD' } },
          {
            componentType: 'data_table',
            title: 'Recent Deals',
            data: [
              { deal: 'Q3 Contract', amount: 50000 },
              { deal: 'Support Plan', amount: 12000 },
            ],
          },
        ],
      },
    }
  }

  return {
    role: 'assistant',
    content: `I received your request: "${userMessage}". In the full implementation, I would query the database, analyze the results, and render the appropriate visualization. Try asking for "trends", "summary", "list all clients", or "dashboard".`,
  }
}
