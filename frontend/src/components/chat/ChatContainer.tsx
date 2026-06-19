import SendIcon from '@mui/icons-material/Send'
import {
  Box,
  CircularProgress,
  IconButton, Paper,
  TextField,
  Typography,
} from '@mui/material'
import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { config } from '../../config'
import { useLLM } from '../../hooks/useLLM'
import { trackEvent } from '../../services/analytics'
import { A2UIRenderer } from '../a2ui/A2UIRenderer'
import type { A2UIResponse } from '../a2ui/A2UITypes'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  a2uiPayload?: A2UIResponse
  timestamp: Date
}

export function ChatContainer() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: config.TEST_MODE
        ? 'Welcome to ContextCanvas (Test Mode). Try asking for "trends", "summary", "list all clients", "dashboard", or "add a new sale".'
        : 'Welcome to ContextCanvas. Ask me anything about your data — I can query your database, visualize results, and help you update records.',
      timestamp: new Date(),
    },
  ])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const { sendMessage } = useLLM()

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = useCallback(async () => {
    const trimmed = input.trim()
    if (!trimmed || isLoading) return

    const userMessage: Message = {
      id: crypto.randomUUID(),
      role: 'user',
      content: trimmed,
      timestamp: new Date(),
    }

    setMessages(prev => [...prev, userMessage])
    setInput('')
    setIsLoading(true)

    try {
      const history = messages.map(({ role, content }) => ({ role, content } as const))
      const response = await sendMessage(trimmed, history)

      const assistantMessage: Message = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.content,
        a2uiPayload: response.a2uiPayload,
        timestamp: new Date(),
      }

      setMessages(prev => [...prev, assistantMessage])

      if (response.a2uiPayload) {
        trackEvent('a2ui_component_rendered', {
          componentType: response.a2uiPayload.componentType,
        })
      } else if (response.content && !response.content.startsWith('⚠️') && !response.content.startsWith('Sorry')) {
        // No A2UI payload rendered — log warning (Failure Point M)
        // The LLM didn't emit A2UI markers or the markers were malformed.
        // Text is still displayed from response.content, but no visualization.
        console.warn('[ChatContainer] LLM response has no A2UI payload. Text displayed without visualization.')
      }
    } catch (error) {
      console.error('Failed to send message:', error)
      setMessages(prev => [...prev, {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: 'Sorry, an error occurred. Please try again.',
        timestamp: new Date(),
      }])
    } finally {
      setIsLoading(false)
    }
  }, [input, isLoading, messages, sendMessage])

  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', maxWidth: 900, mx: 'auto', width: '100%' }}>
      {/* Header */}
      <Paper sx={{ p: 2, borderRadius: 0, borderBottom: 1, borderColor: 'divider' }} elevation={0}>
        <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          🎨 ContextCanvas
          <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
            {config.TEST_MODE ? 'Test Mode' : 'AI-Powered Workspace PoC'}
          </Typography>
        </Typography>
      </Paper>

      {/* Messages */}
      <Box sx={{ flex: 1, overflowY: 'auto', p: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
        {messages.map((msg) => (
          <Box key={msg.id} sx={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
            <Paper
              sx={{
                p: 2,
                maxWidth: '80%',
                bgcolor: msg.role === 'user' ? 'primary.main' : 'background.paper',
                color: msg.role === 'user' ? 'white' : 'text.primary',
                borderRadius: 2,
              }}
              elevation={1}
            >
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{msg.content}</Typography>
              {msg.a2uiPayload && (
                <Box sx={{ mt: 2 }}>
                  <A2UIRenderer payload={msg.a2uiPayload} />
                </Box>
              )}
            </Paper>
          </Box>
        ))}

        {isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'flex-start' }}>
            <Paper sx={{ p: 2, borderRadius: 2 }} elevation={1}>
              <CircularProgress size={20} />
            </Paper>
          </Box>
        )}
        <div ref={messagesEndRef} />
      </Box>

      {/* Input */}
      <Paper sx={{ p: 2, borderTop: 1, borderColor: 'divider' }} elevation={0}>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <TextField
            fullWidth
            size="small"
            placeholder="Ask about your data, request changes, or explore insights..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isLoading}
            slotProps={{
              htmlInput: { 'data-testid': 'chat-input' },
              input: { sx: { borderRadius: 2 } },
            }}
          />
          <IconButton
            onClick={handleSend}
            disabled={!input.trim() || isLoading}
            color="primary"
            sx={{ bgcolor: 'primary.main', color: 'white', '&:hover': { bgcolor: 'primary.dark' } }}
          >
            <SendIcon />
          </IconButton>
        </Box>
      </Paper>
    </Box>
  )
}
