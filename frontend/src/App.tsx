import { ThemeProvider, CssBaseline, Box } from '@mui/material'
import { theme } from './styles/theme'
import { ChatContainer } from './components/chat/ChatContainer'

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box sx={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
        <ChatContainer />
      </Box>
    </ThemeProvider>
  )
}