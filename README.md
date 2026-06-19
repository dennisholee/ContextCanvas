# ContextCanvas — AI-Powered Workspace PoC

ContextCanvas transforms traditional conversational AI from a static text-and-markdown chat window into a fluid, adaptive graphical user interface built on an **agent-driven UI protocol (A2UI)**.

The AI **discovers the database schema dynamically, learns user preferences across sessions, and proactively surfaces insights** — making it a true autonomous agent rather than a reactive query tool.

---

## Table of Contents

- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Features](#features)
- [Configuration](#configuration)
- [How to Run](#how-to-run)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [License](#license)

---

## Quick Start

### Prerequisites

- **Frontend:** Node.js 22+
- **Backend:** Java 21+ JDK (optional — frontend works without it when using test mode)
- **Docker & Docker Compose** (optional — for full-stack deployment)
- **LLM API Key** (optional — for real AI responses; test mode works without one)

### Run the Frontend

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. By default the frontend connects to a backend at `http://localhost:8080`. To enable simulated responses (no backend needed), set:

```bash
VITE_TEST_MODE=true npm run dev
```

Try these queries:
- `Show me monthly trends` → renders a line chart
- `Show me summary` → renders a metric card
- `List all clients` → renders a data table
- `Show me the full picture` → renders a composite dashboard
- `Create new client` → renders a confirmation card with Approve/Cancel

### Run the Backend (Full AI Mode)

For real AI responses with database access, you need an LLM API key (OpenAI, DeepSeek, or Anthropic).

```bash
cd backend
./mvnw clean package -DskipTests
export CONTEXTCANVAS_LLM_API_KEY=sk-your-key-here
java -jar target/contextcanvas-mcp-server-*.jar
```

Then start the frontend (from another terminal):

```bash
cd frontend
npm install
npm run dev
```

### Run with Docker Compose

```bash
export CONTEXTCANVAS_LLM_API_KEY=sk-your-key-here
docker compose up --build
```

Access the app at **http://localhost:5173**.

---

## Architecture

```
Browser (React 19 + MUI 6 + Vite 6)
       ↕  HTTP POST /api/chat
Spring Boot Backend (Java 21)
       ↕  LLM API + Tool Dispatcher
LLM Provider (OpenAI / DeepSeek / Anthropic)
       ↕  Function-Calling Loop (max 8 iterations)
MCP Tools (6 tools: query, execute, introspect, etc.)
       ↕  JDBC
SQLite database (3 business tables + preferences)
```

### Key Architecture Decisions

1. **Function-calling loop runs on the backend.** The LLM API key stays on the server. The frontend is a thin HTTP POST client — it sends the user's message and receives the AI response with optional A2UI visualization JSON.

2. **Single ToolDispatcher for all transports.** Both the HTTP REST endpoint (`/api/mcp`) and the stdio JSON-RPC server delegate to the same `ToolDispatcher`, ensuring consistent behavior regardless of transport.

3. **Schema introspection at session start.** The agent queries `sqlite_master` and `PRAGMA table_info` to discover tables, columns, and foreign keys dynamically. No hardcoded entity-specific tools needed.

### MCP Tools

| Tool | Purpose |
|---|---|
| `introspect_schema` | Returns full database schema (tables, columns, FKs) |
| `query` | Parameterized SELECT execution (read-only) |
| `execute` | INSERT/UPDATE/DELETE with two-phase confirmation gate |
| `resolve_entity` | Fuzzy entity resolution (exact → LIKE → contact match) |
| `track_preference` | Persists user preferences across sessions |
| `get_preferences` | Retrieves all saved preferences |

### A2UI Protocol

The LLM outputs structured JSON wrapped in `---A2UI_START---` / `---A2UI_END---` markers. The frontend parses this JSON and renders the corresponding MUI component. Supported component types:

- `line_chart`, `bar_chart`, `pie_chart`, `area_chart`
- `metric_card`, `data_table`
- `dashboard` (composite grid layout)
- `confirmation_card` (write approval with Approve/Cancel)

---

## Features

- **Schema introspection** — agent discovers database structure automatically at session start
- **Dynamic SQL generation** — agent writes parameterized queries using discovered table/column names
- **Safety gate** — every write requires two-phase confirmation with 60-second expiring token
- **Cross-session memory** — user preferences persist across sessions via `user_preferences` table
- **Fuzzy entity resolution** — 3-stage matching chain (exact → LIKE → contact name)
- **A2UI visualization** — AI decides what charts, tables, and cards to render
- **SQL injection prevention** — all queries use `?` placeholders with parameter validation
- **DDL rejection** — CREATE/ALTER/DROP statements are blocked by validation

---

## Configuration

All configuration is via environment variables with sensible defaults:

### Backend

| Variable | Default | Description |
|---|---|---|
| `CONTEXTCANVAS_PORT` | `8080` | Server port |
| `CONTEXTCANVAS_DB_PATH` | `./data/contextcanvas.db` | SQLite database file path |
| `CONTEXTCANVAS_LLM_API_KEY` | *(empty)* | LLM provider API key (empty = test mode) |
| `CONTEXTCANVAS_LLM_API_URL` | `https://api.openai.com/v1` | LLM API base URL |
| `CONTEXTCANVAS_LLM_MODEL` | `gpt-4o` | LLM model name |
| `CONTEXTCANVAS_LLM_TEST_MODE` | `false` | If true, uses simulated responses |
| `CONTEXTCANVAS_LOG_LEVEL` | `INFO` | Backend log level |

### Frontend

| Variable | Default | Description |
|---|---|---|
| `VITE_MCP_API_URL` | `http://localhost:8080/api/mcp` | Backend MCP endpoint URL |
| `VITE_POSTHOG_KEY` | *(empty)* | PostHog analytics key (optional) |
| `VITE_TEST_MODE` | `false` | If true, uses simulated responses (no backend needed) |

---

## How to Run

### Frontend Only (Test Mode)

```bash
cd frontend
npm install
VITE_TEST_MODE=true npm run dev
```

Opens at **http://localhost:5173** with simulated AI responses. No backend needed.

### Backend Only

```bash
cd backend
./mvnw clean package -DskipTests
export CONTEXTCANVAS_LLM_API_KEY=sk-your-key-here
java -jar target/contextcanvas-mcp-server-*.jar
```

### Full Stack (Local)

```bash
# Terminal 1: Backend
cd backend
./mvnw clean package -DskipTests
export CONTEXTCANVAS_LLM_API_KEY=sk-your-key-here
java -jar target/contextcanvas-mcp-server-*.jar

# Terminal 2: Frontend
cd frontend
npm install
npm run dev
```

### Full Stack (Docker)

```bash
export CONTEXTCANVAS_LLM_API_KEY=sk-your-key-here
docker compose up --build
```

---

## Running Tests

### Backend Tests (Java Unit + Integration)

```bash
cd backend
./mvnw clean verify
```

Runs 13 end-to-end tests against a real SQLite database with Flyway seed data:
- Schema introspection, query execution, confirmation gate, token expiry
- SQL injection prevention, entity resolution, preference CRUD
- No mocks — all tests run against the actual Spring Boot application context

### Frontend Tests (Playwright E2E)

```bash
cd frontend
npx playwright test
```

Validates A2UI component rendering (line chart, metric card, data table, dashboard, confirmation card) and the write approval flow. Screenshots captured in `frontend/test-results/`.

---

## Project Structure

```
contextcanvas/
├── backend/                           # Java MCP Server (Spring Boot 3.4 + SQLite)
│   ├── src/main/java/com/contextcanvas/server/
│   │   ├── ContextCanvasApplication.java   # Spring Boot entry point
│   │   ├── config/                        # Spring Boot configuration
│   │   │   ├── ChatController.java         # REST endpoint: POST /api/chat
│   │   │   ├── DatabaseConfiguration.java  # SQLite datasource + HikariCP
│   │   │   ├── McpConfiguration.java       # MCP server bean wiring
│   │   │   ├── McpRestController.java      # REST transport: GET /api/mcp/tools, POST /api/mcp/call
│   │   │   └── McpServer.java             # stdio JSON-RPC transport
│   │   ├── model/                         # Data models (records)
│   │   │   ├── ColumnDefinition.java
│   │   │   ├── ExecutionResult.java
│   │   │   ├── ForeignKeyDefinition.java
│   │   │   ├── QueryResult.java
│   │   │   └── TableDefinition.java
│   │   ├── service/                       # Core business logic
│   │   │   ├── LlmChatService.java        # LLM function-calling loop (max 8 iterations)
│   │   │   ├── PreferenceService.java     # Cross-session preference CRUD
│   │   │   ├── QueryExecutionService.java # Parameterized SELECT execution
│   │   │   ├── SchemaIntrospectionService.java  # SQLite schema discovery
│   │   │   ├── ToolDispatcher.java        # Single dispatch hub for all MCP tools
│   │   │   └── WriteExecutionService.java # Two-phase write with token-based confirmation
│   │   ├── tool/                          # MCP tool implementations
│   │   │   ├── ExecuteTool.java           # INSERT/UPDATE/DELETE with safety gate
│   │   │   ├── GetPreferencesTool.java    # Retrieve all user preferences
│   │   │   ├── IntrospectSchemaTool.java  # Database schema introspection
│   │   │   ├── McpTool.java              # Tool interface (4 methods)
│   │   │   ├── QueryTool.java            # Parameterized SELECT
│   │   │   ├── ResolveEntityTool.java    # Fuzzy entity resolution
│   │   │   └── TrackPreferenceTool.java  # Save user preference
│   │   └── validation/                    # SQL safety validation
│   │       ├── EntityResolver.java        # 3-stage entity matching
│   │       ├── SqlValidationException.java
│   │       └── SqlValidator.java          # DDL/DML/parameter validation
│   ├── src/main/resources/
│   │   ├── application.yml                # Backend configuration
│   │   └── db/migration/                  # Flyway SQL migrations (V1-V5)
│   └── src/test/                          # E2E tests
├── frontend/                              # React 19 + TypeScript + Vite
│   ├── src/components/a2ui/               # A2UI renderer + 9 component types
│   ├── src/components/chat/               # Chat container with LLM integration
│   ├── src/hooks/                         # useLLM, useChat hooks
│   ├── src/config/                        # Environment variable configuration
│   ├── src/services/                      # Analytics (PostHog)
│   ├── src/styles/                        # MUI theme customization
│   └── e2e/                               # Playwright E2E tests
├── docker-compose.yml                     # Full-stack Docker deployment
└── ARCHITECTURE.md                        # Complete architecture guide
```

---

## License

This project is open source. See the repository for license details.
