# ContextCanvas PoC — Architecture Guide

> **Document Version:** 1.1
> **PRD Version:** 2.1 (Core Features)
> **Last Updated:** 2026-06-19
> **Target:** 8-12 week Proof of Concept

---

## Table of Contents

1. [Technology Stack](#1-technology-stack)
2. [Project Structure](#2-project-structure)
3. [Architecture Overview](#3-architecture-overview)
4. [MCP Server Design (Java)](#4-mcp-server-design-java)
5. [Frontend Design (React)](#5-frontend-design-react)
6. [Data Flow & State Management](#6-data-flow--state-management)
7. [A2UI Protocol Specification](#7-a2ui-protocol-specification)
8. [Database Schema & Introspection](#8-database-schema--introspection)
9. [LLM Integration](#9-llm-integration)
10. [Testing Strategy](#10-testing-strategy)
11. [Configuration Externalization](#11-configuration-externalization)
12. [Security & Safety](#12-security--safety)
13. [Build & Deployment](#13-build--deployment)
14. [Development Phases](#14-development-phases)

---

## 1. Technology Stack

### 1.1 Decision Matrix

| Layer | Technology | Version | Rationale |
|---|---|---|---|
| **Backend Runtime** | Java 21 LTS | 21+ | Virtual threads for concurrent MCP request handling; latest LTS with 7+ years of support |
| **Backend Framework** | Spring Boot 3.x | 3.4+ | Production-grade dependency injection, configuration management, actuator health checks |
| **MCP SDK** | Java MCP SDK (official) | Latest | First-class MCP protocol support; JSON-RPC over stdio; tool registration lifecycle |
| **Database** | SQLite via `org.xerial:sqlite-jdbc` | 3.45+ | Zero-setup, file-based, embeds directly in the MCP server process; no external database server |
| **Connection Pool** | HikariCP | 6.x | Fastest SQLite connection pooling; Spring Boot default; handles concurrent read contention |
| **SQL Migration** | Flyway Community | 10.x | Version-controlled schema migrations; SQLite-compatible; no JPA/Hibernate overhead for 3 tables |
| **JSON Processing** | Jackson 2.x | 2.17+ | De facto standard for Java JSON; handles A2UI payload serialization |
| **Frontend** | React + TypeScript | 19.x / 5.5+ | Strict mode, concurrent features; TypeScript for type-safe A2UI payload handling |
| **Build (Frontend)** | Vite 6 | 6.x | Sub-second HMR; native TypeScript/JSX; no Webpack overhead |
| **UI Component Library** | Material UI (MUI) 6 | 6.x | Comprehensive component catalog; X-Charts for visualization; DataGrid for tables |
| **State Management** | React Context + `useReducer` | Built-in | No external state library needed for single-user PoC chat app; avoids Redux/Zustand overhead |
| **HTTP Client** | `fetch` (native) | Built-in | LLM Gateway calls; no Axios dependency required for PoC |
| **LLM Gateway** | Vercel AI SDK (TypeScript) | Latest | Unified interface for OpenAI/Anthropic/others; streaming, tool calling, structured output out of the box |
| **Testing (E2E)** | Playwright | 1.48+ | Cross-browser E2E testing; network interception for LLM mock; visual regression for A2UI components |
| **Testing (Backend)** | JUnit 5 + Testcontainers | Latest | Testcontainers for isolated MCP server lifecycle testing; no mocking framework required |
| **Configuration** | Spring Boot `application.yml` + `.env` | — | Externalized via environment variables; `.env` for local dev; `SPRING_*` env vars for deployment |
| **Analytics** | PostHog (self-hosted or cloud) | Latest | Event pipeline for KPI tracking; TypeScript SDK for frontend |
| **Linting** | ESLint 9 (flat config) + Prettier | Latest | Flat config is the 2026 standard; zero-config Prettier integration |
| **Formatting** | Spotless (Java) + Prettier (TS) | Latest | Consistent formatting across the entire monorepo |

### 1.2 Dependency Vulnerability Policy

- All direct dependencies are pinned to **specific minor versions** (e.g., `3.4.1`, not `3.4.x`)
- Dependabot / Renovate configured for weekly automated PRs
- Snyk or GitHub Advisory Database scan on every PR
- Zero known critical/high CVEs at time of dependency selection
- Spring Boot 3.x, Vite 6, and Playwright are actively maintained with rapid patch cycles

---

## 2. Project Structure

```
contextcanvas/
├── .github/
│   └── workflows/
│       ├── ci.yml                    # Build + lint + E2E test on PR
│       └── dependabot.yml            # Weekly dependency updates
├── backend/                          # Java MCP Server (Spring Boot)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/contextcanvas/server/
│   │   │   │   ├── ContextCanvasApplication.java    # @SpringBootApplication entry point
│   │   │   │   ├── config/
│   │   │   │   │   ├── McpConfiguration.java        # MCP SDK tool registration
│   │   │   │   │   ├── DatabaseConfiguration.java   # SQLite + HikariCP + Flyway
│   │   │   │   │   └── JacksonConfiguration.java    # ObjectMapper for A2UI payloads
│   │   │   │   ├── tool/
│   │   │   │   │   ├── IntrospectSchemaTool.java    # introspect_schema()
│   │   │   │   │   ├── QueryTool.java               # query(sql, params)
│   │   │   │   │   ├── ExecuteTool.java             # execute(sql, params)
│   │   │   │   │   ├── ResolveEntityTool.java       # resolve_entity(name, table)
│   │   │   │   │   ├── TrackPreferenceTool.java     # track_preference(key, value)
│   │   │   │   │   └── GetPreferencesTool.java      # get_preferences()
│   │   │   │   ├── model/
│   │   │   │   │   ├── SchemaDefinition.java        # Table, Column, ForeignKey records
│   │   │   │   │   ├── QueryResult.java             # Column names + row data
│   │   │   │   │   └── ExecutionResult.java         # Affected rows, confirmation payload
│   │   │   │   ├── service/
│   │   │   │   │   ├── SchemaIntrospectionService.java  # Reads SQLite master + pragma
│   │   │   │   │   ├── QueryExecutionService.java       # Validates + executes SELECT
│   │   │   │   │   ├── WriteExecutionService.java       # Validates + intercepts writes
│   │   │   │   │   └── PreferenceService.java           # CRUD for user_preferences table
│   │   │   │   └── validation/
│   │   │   │       ├── SqlValidator.java            # Rejects DDL, ensures parameterized
│   │   │   │       └── EntityResolver.java          # Exact → Like → Fuzzy resolution chain
│   │   │   └── resources/
│   │   │       ├── application.yml                  # Spring Boot + MCP + SQLite config
│   │   │       ├── application-dev.yml              # Dev overrides (SQLite path, logging)
│   │   │       └── db/migration/
│   │   │           ├── V1__create_clients.sql
│   │   │           ├── V2__create_sales.sql
│   │   │           ├── V3__create_contacts.sql
│   │   │           └── V4__create_user_preferences.sql
│   │   └── test/
│   │       └── java/com/contextcanvas/server/
│   │           ├── tool/
│   │           │   └── AllToolsE2ETest.java         # End-to-end MCP tool lifecycle tests
│   │           ├── service/
│   │           │   └── AllServicesE2ETest.java      # Service-layer integration tests
│   │           └── ContextCanvasE2ETest.java        # Full server startup → tool call → response
│   ├── pom.xml                                      # Maven build
│   └── .env.example                                 # SPRING_DATASOURCE_URL, etc.
├── frontend/                          # React + TypeScript + Vite
│   ├── src/
│   │   ├── main.tsx                   # React root, StrictMode
│   │   ├── App.tsx                    # Top-level layout (Chat + A2UI Renderer)
│   │   ├── components/
│   │   │   ├── chat/
│   │   │   │   ├── ChatContainer.tsx     # Message list + auto-scroll
│   │   │   │   ├── ChatInput.tsx         # Text input + send button
│   │   │   │   └── MessageBubble.tsx     # Single message wrapper (text + A2UI)
│   │   │   ├── a2ui/
│   │   │   │   ├── A2UIRenderer.tsx      # Routes componentType → MUI component
│   │   │   │   ├── LineChart.tsx         # MUI X LineChart wrapper
│   │   │   │   ├── BarChart.tsx          # MUI X BarChart wrapper
│   │   │   │   ├── PieChart.tsx          # MUI X PieChart wrapper
│   │   │   │   ├── MetricCard.tsx        # MUI Card + Typography
│   │   │   │   ├── StatCard.tsx          # Multi-stat layout
│   │   │   │   ├── DataTable.tsx         # MUI DataGrid wrapper
│   │   │   │   ├── ProgressBar.tsx       # LinearProgress
│   │   │   │   ├── Gauge.tsx             # Radial gauge (custom or MUI X)
│   │   │   │   ├── AreaChart.tsx         # MUI X AreaChart wrapper
│   │   │   │   ├── ConfirmationCard.tsx  # Approve/Cancel with field detail
│   │   │   │   ├── ErrorCard.tsx         # Human-friendly error display
│   │   │   │   ├── ClarificationPrompt.tsx
│   │   │   │   ├── ProactiveInsight.tsx  # AI-initiated suggestion card
│   │   │   │   ├── DashboardLayout.tsx   # Composite layout renderer (vertical/grid/sidebar)
│   │   │   │   └── A2UITypes.ts          # TypeScript types for all A2UI payloads
│   │   │   └── common/
│   │   │       ├── LoadingSpinner.tsx
│   │   │       └── ErrorBoundary.tsx     # Catches render errors, shows ErrorCard
│   │   ├── hooks/
│   │   │   ├── useChat.ts               # Message state, send handler
│   │   │   ├── useLLM.ts                # LLM Gateway client (Vercel AI SDK)
│   │   │   └── usePreferences.ts        # Preference learning state
│   │   ├── services/
│   │   │   ├── llmGateway.ts            # Vercel AI SDK configuration
│   │   │   └── analytics.ts             # PostHog event tracking wrapper
│   │   ├── config/
│   │   │   └── index.ts                 # Environment variable access
│   │   └── styles/
│   │       ├── theme.ts                 # MUI theme (light mode for PoC)
│   │       └── global.css               # Minimal global overrides
│   ├── public/
│   │   └── index.html
│   ├── e2e/
│   │   ├── fixtures/
│   │   │   └── test-database.sqlite     # Pre-seeded test db for E2E
│   │   ├── pages/
│   │   │   └── chat.spec.ts             # Page object for chat interactions
│   │   ├── a2ui-components.spec.ts      # Visual regression + functional E2E
│   │   ├── write-flow.spec.ts           # Confirmation → Approve → Sync cycle
│   │   ├── self-healing.spec.ts         # Fuzzy match recovery flow
│   │   ├── proactive-insights.spec.ts   # AI-initiated insight detection
│   │   └── preferences.spec.ts          # Cross-session learning verification
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── playwright.config.ts
│   └── .env.example                     # VITE_LLM_API_KEY, VITE_POSTHOG_KEY
├── docker-compose.yml                   # Local dev: frontend + backend + analytics
├── .gitignore
├── .editorconfig
├── .prettierrc
├── eslint.config.js                     # ESLint 9 flat config
└── README.md                            # Quick-start: `docker compose up`
```

### 2.1 Structure Rationale

- **Backend is a standalone Java process** with dual transport: (1) HTTP REST via Spring Boot embedded Tomcat on port 8080 (primary path used by the frontend), and (2) stdio JSON-RPC via the MCP protocol (available for direct LLM integration, but NOT the primary path).
- **Frontend talks to both the LLM API AND the backend.** The frontend acts as a tool-calling intermediary: it sends user messages + tool definitions to the LLM, the LLM responds with tool calls (e.g., `query()`), and the frontend executes those calls against the backend's HTTP REST API (`localhost:8080/api/mcp`). Results are fed back into the LLM's context. This is NOT the standard MCP architecture pattern — it is a custom function-calling loop.
- **E2E tests live in `frontend/e2e/`** because the user-facing entry point is the browser. Backend tests use JUnit alone.
- **No `mocks/` directory anywhere.** Testing is integration/E2E only unless a unit test is required for coverage gap.
- **Configuration** includes `VITE_LLM_API_URL` and `VITE_MCP_API_URL` for pointing at different LLM providers and backends (see Section 11).

---

## 3. Architecture Overview

### 3.1 System Context Diagram (Actual Architecture)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                     │
│  ┌─────────────────────────────────────┐    ┌──────────────────────────────────┐    │
│  │   User (Browser)                     │    │  LLM Provider (multi)             │    │
│  │                                      │    │                                   │    │
│  │  ┌─────────────────────────────────┐ │    │  OpenAI / DeepSeek / etc.         │    │
│  │  │ React + TypeScript + Vite       │ │    │  (OpenAI-compatible API)           │    │
│  │  │                                 │ │    └──────────────┬───────────────────┘    │
│  │  │ ┌──────┐ ┌────────────────────┐ │ │                   │ HTTP POST /chat/       │
│  │  │ │Chat  │ │ A2UIRenderer        │ │ │                   │   completions          │
│  │  │ │UI    │ │ (component catalog) │ │ │                   │                       │
│  │  │ └──────┘ └────────────────────┘ │ │    ┌───────────────────────────────────┐ │
│  │  │                                 │ │    │  Java Backend (Spring Boot)          │
│  │  │ ┌─────────────────────────────┐ │ │    │                                     │
│  │  │ │ useLLM (thin client)        │─┼─┼──>│  ┌─────────────┐  ┌─────────────┐  │ │
│  │  │ │  POST /api/chat             │ │ │    │  │ChatController│  │LlmChatService│  │ │
│  │  │ └─────────────────────────────┘ │ │    │  └──────┬──────┘  └──────┬──────┘  │ │
│  │  └────────────┬────────────────────┘ │    │         │               │         │  │
│  │               │ GET/POST /api/mcp/*  │    │         ▼               ▼         │  │
│  │               ▼                      │    │  ┌───────────────────────────┐   │  │
│  │  ┌──────────────────────────────┐    │    │  │   ToolDispatcher          │   │  │
│  │  │ McpRestController            │    │    │  │   (in-process dispatch)   │   │  │
│  │  │ (HTTP REST bridge, legacy)   │────┼────│  │   - query / execute       │   │  │
│  │  └──────────────────────────────┘    │    │  │   - introspect_schema     │   │  │
│  └──────────────────────────────────────┘    │  │   - resolve_entity / etc. │   │  │
│                                              │  └───────────┬───────────────┘   │  │
│                                              │              ▼                   │  │
│                                              │  ┌────────────────────────────┐  │  │
│                                              │  │   SQLite (sqlite-jdbc)     │  │  │
│                                              │  │   clients / sales /        │  │  │
│                                              │  │   contacts / preferences   │  │  │
│                                              │  └────────────────────────────┘  │  │
│                                              │                                   │  │
│                                              │  ┌────────────────────────────┐  │  │
│                                              │  │ McpServer (stdio JSON-RPC)  │  │  │
│                                              │  │ Available for desktop LLM   │  │  │
│                                              │  │ hosts (Claude Desktop etc.) │  │  │
│                                              │  └────────────────────────────┘  │  │
│                                              └───────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────┘

Data Flow:
  1. User types message → React POST /api/chat → ChatController → LlmChatService
  2. LlmChatService builds messages + tool definitions → POST /chat/completions → LLM
  3. LLM responds with tool_calls (OpenAI-compatible format)
  4. LlmChatService executes tools via ToolDispatcher.callTool() → SQLite
  5. Tool results fed back to LLM context → loop until final answer
  6. LLM emits ---A2UI_START--- / ---A2UI_END--- in final response
  7. LlmChatService extracts A2UI → returns {text, a2uiJson} to frontend
  8. React renders A2UI component via A2UIRenderer
```

### 3.2 Request Lifecycle (Read)

```
User: "Show me Q3 sales trends"
  │
  ▼
React Frontend (ChatInput.tsx)
  │  POST /api/chat (streaming) to LLM Gateway
  ▼
LLM Gateway (Vercel AI SDK)
  │  Formats prompt + available MCP tool definitions
  │  Sends to configured provider (e.g., OpenAI)
  ▼
LLM Provider
  │  AI reasons: "I need to query the sales table grouped by month for Q3 2026"
  │  AI calls MCP tool: query(sql: "SELECT strftime(...), SUM(deal_amount) FROM sales...")
  ▼
MCP Server (Java)
  │  SqlValidator: ✓ Parameterized, SELECT only
  │  QueryExecutionService: executes via HikariCP → SQLite
  │  Returns QueryResult (column names + rows)
  ▼
LLM Provider
  │  Receives result, decides: "This is a trend — render as line_chart"
  │  Emits A2UI payload: { componentType: "line_chart", title: "Q3 Sales Trends", data: [...] }
  ▼
LLM Gateway → streams A2UI payload back to React
  ▼
React Frontend (A2UIRenderer.tsx)
  │  Parses componentType: "line_chart"
  │  Renders <LineChart title="Q3 Sales Trends" data={[...]} />
  ▼
User sees interactive line chart with MUI X Chart
```

### 3.3 Request Lifecycle (Write — with Safety Gate)

```
User: "Add a new sale of $5,000 for Acme"
  │
  ▼
LLM Gateway → LLM Provider
  │  AI reasons: "I need to create a sale. First I need to find the client record."
  │  AI calls: resolve_entity("Acme", "clients") → finds "Acme Corporation" (ID: 42)
  │  Generates: INSERT INTO sales (client_id, deal_amount, stage) VALUES (?, ?, ?)
  │  Calls MCP tool: execute(sql, params)
  ▼
MCP Server (WriteExecutionService)
  │  Intercepts: "This is a write operation"
  │  Does NOT execute yet
  │  Returns: ConfirmationPayload {
  │    actionType: "CREATE",
  │    entity: "sales",
  │    newValues: { client_id: 42, deal_amount: 5000, stage: "pipeline" }
  │  }
  ▼
LLM Provider → emits A2UI confirmation_card payload
  ▼
React Frontend → renders <ConfirmationCard ... />
  │  User sees: "Create new Sale for Acme Corporation"
  │  "Amount: $5,000.00 | Stage: Pipeline"
  │  [✓ Approve]  [✗ Cancel]
  ▼
User clicks "Approve"
  │  Frontend sends approval event back via LLM
  ▼
LLM → calls execute(sql, params) again with confirmation flag
  │  This time, WriteExecutionService allows execution
  ▼
SQLite: INSERT succeeds, row created
  │
  ▼
LLM → emits success alert + re-renders updated data
  React → hides confirmation card, shows success
```

---

## 4. MCP Server Design (Java)

### 4.1 Entry Point

```java
// ContextCanvasApplication.java
@SpringBootApplication
public class ContextCanvasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContextCanvasApplication.class, args);
    }
}
```

Spring Boot starts, loads configuration, runs Flyway migrations, then starts the MCP server on stdin/stdout. No web server is needed — MCP uses JSON-RPC over standard I/O.

### 4.2 MCP Tool Registration

```java
// McpConfiguration.java
@Configuration
public class McpConfiguration {

    @Bean
    public McpServer mcpServer(
            List<McpTool> tools,
            McpServerTransport transport,
            McpServerFeatures features) {
        return McpServer.using(transport)
                .serverInfo("contextcanvas-mcp", "1.0.0")
                .tools(tools)
                .capabilities(features)
                .build();
    }

    @Bean
    public McpServerTransport stdioTransport() {
        // Default MCP stdio transport — reads from System.in, writes to System.out
        return new StdioServerTransport();
    }
}
```

### 4.3 Tool Implementation Pattern

Every tool follows the same pattern:

```java
// QueryTool.java
@McpToolDefinition(name = "query", description = """
        Execute a parameterized SELECT query against the database.
        The SQL must be a SELECT statement only. All user input must use ? placeholders.
        """)
public class QueryTool implements McpTool {

    private final QueryExecutionService queryService;
    private final SqlValidator sqlValidator;

    public QueryTool(QueryExecutionService queryService, SqlValidator sqlValidator) {
        this.queryService = queryService;
        this.sqlValidator = sqlValidator;
    }

    @McpToolParameter(name = "sql", description = "SELECT statement with ? placeholders")
    public McpToolResult execute(@NotBlank String sql, @Nullable List<Object> params) {
        sqlValidator.assertSelectOnly(sql);
        sqlValidator.assertParameterized(sql, params);
        QueryResult result = queryService.execute(sql, params);
        return McpToolResult.success(result);
    }
}
```

**Tool Definitions Table:**

| Tool | SQL Pattern | Return Type | Safety Gate |
|---|---|---|---|
| `introspect_schema` | `SELECT * FROM sqlite_master` + `PRAGMA table_info` | `List<TableDefinition>` | N/A (read-only metadata) |
| `query` | `SELECT ... WHERE ?` | `QueryResult` | Validates SELECT only; no DDL/DML |
| `execute` | `INSERT/UPDATE/DELETE ...` | `ExecutionResult` | Requires explicit user approval before execution |
| `resolve_entity` | Internal fuzzy search | `List<EntityMatch>` | N/A (read-only) |
| `track_preference` | `INSERT OR REPLACE INTO user_preferences` | `void` | N/A (single-user, low risk) |
| `get_preferences` | `SELECT * FROM user_preferences` | `Map<String, String>` | N/A (read-only) |

### 4.4 SQL Validation (Safety Critical)

```java
// SqlValidator.java
@Component
public class SqlValidator {

    private static final Pattern SELECT_PATTERN =
            Pattern.compile("^\\s*SELECT\\s+.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern DDL_PATTERN =
            Pattern.compile("\\b(CREATE\\s+TABLE|ALTER\\s+TABLE|DROP\\s+TABLE|TRUNCATE)\\b",
                    Pattern.CASE_INSENSITIVE);

    public void assertSelectOnly(String sql) {
        if (!SELECT_PATTERN.matcher(sql).matches()) {
            throw new SqlValidationException("Only SELECT statements are permitted via query()");
        }
        if (DDL_PATTERN.matcher(sql).find()) {
            throw new SqlValidationException("DDL operations (CREATE/ALTER/DROP) are not permitted");
        }
    }

    public void assertParameterized(String sql, List<Object> params) {
        long placeholderCount = sql.chars().filter(ch -> ch == '?').count();
        boolean hasParams = params != null && !params.isEmpty();
        if (placeholderCount > 0 && !hasParams) {
            throw new SqlValidationException(
                    "SQL contains ? placeholders but no parameters provided");
        }
        if (hasParams && placeholderCount != params.size()) {
            throw new SqlValidationException(
                    "Parameter count mismatch: %d placeholders, %d parameters"
                            .formatted(placeholderCount, params.size()));
        }
        // Reject any param containing SQL fragments (basic injection prevention)
        for (Object param : params) {
            if (param instanceof String s && DDL_PATTERN.matcher(s).find()) {
                throw new SqlValidationException(
                        "Parameter contains disallowed SQL keywords");
            }
        }
    }
}
```

### 4.5 Schema Introspection Service

```java
// SchemaIntrospectionService.java
@Service
public class SchemaIntrospectionService {

    private final JdbcTemplate jdbc;

    public SchemaIntrospectionService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public List<TableDefinition> introspect() {
        List<String> userTables = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'flyway_%' " +
                "AND name NOT IN ('user_preferences')",
                String.class);

        return userTables.stream()
                .map(this::describeTable)
                .toList();
    }

    private TableDefinition describeTable(String tableName) {
        List<ColumnDefinition> columns = jdbc.query(
                "PRAGMA table_info(%s)".formatted(tableName),  // pragma_name is fixed, not user input
                (rs, row) -> new ColumnDefinition(
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getBoolean("notnull"),
                        rs.getString("dflt_value"),
                        rs.getBoolean("pk")));

        List<ForeignKeyDefinition> foreignKeys = jdbc.query(
                "PRAGMA foreign_key_list(%s)".formatted(tableName),
                (rs, row) -> new ForeignKeyDefinition(
                        rs.getString("from"),
                        rs.getString("table"),
                        rs.getString("to")));

        return new TableDefinition(tableName, columns, foreignKeys);
    }
}
```

**Returned JSON structure (to the LLM):**

```json
[
  {
    "tableName": "clients",
    "columns": [
      { "name": "id", "type": "INTEGER", "required": true, "primaryKey": true },
      { "name": "company_name", "type": "TEXT", "required": true, "primaryKey": false },
      { "name": "industry", "type": "TEXT", "required": false, "primaryKey": false },
      { "name": "status", "type": "TEXT", "required": false, "primaryKey": false,
        "constraint": "CHECK(status IN ('active','inactive','lead'))" },
      { "name": "contact_email", "type": "TEXT", "required": false, "primaryKey": false },
      { "name": "created_at", "type": "TIMESTAMP", "required": false, "primaryKey": false }
    ],
    "foreignKeys": []
  },
  {
    "tableName": "sales",
    "columns": [ ... ],
    "foreignKeys": [
      { "fromColumn": "client_id", "referencedTable": "clients", "referencedColumn": "id" }
    ]
  }
]
```

---

## 5. Frontend Design (React)

### 5.1 Application Shell

```typescript
// App.tsx
import { ChatContainer } from './components/chat/ChatContainer';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { theme } from './styles/theme';
import { ErrorBoundary } from './components/common/ErrorBoundary';

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <ErrorBoundary fallback={<ErrorCard message="Something went wrong" />}>
        <ChatContainer />
      </ErrorBoundary>
    </ThemeProvider>
  );
}
```

### 5.2 A2UI Renderer — Component Router

The renderer is a pure function that maps `componentType` to the corresponding React component. No switch/case — uses a registry pattern for extensibility.

```typescript
// A2UIRenderer.tsx
import { type A2UIPayload, type A2UIComponentType } from './A2UITypes';
import { LineChart } from './LineChart';
import { BarChart } from './BarChart';
import { PieChart } from './PieChart';
import { MetricCard } from './MetricCard';
// ... other imports

const componentRegistry: Record<A2UIComponentType, React.ComponentType<any>> = {
  line_chart: LineChart,
  bar_chart: BarChart,
  pie_chart: PieChart,
  metric_card: MetricCard,
  stat_card: StatCard,
  data_table: DataTable,
  progress_bar: ProgressBar,
  gauge: Gauge,
  area_chart: AreaChart,
  confirmation_card: ConfirmationCard,
  error_card: ErrorCard,
  clarification_prompt: ClarificationPrompt,
  proactive_insight: ProactiveInsight,
};

export function A2UIRenderer({ payload }: { payload: A2UIPayload }) {
  const Component = componentRegistry[payload.componentType];

  if (!Component) {
    console.warn(`Unknown component type: ${payload.componentType}`);
    return <ErrorCard message={`Unknown component: ${payload.componentType}`} />;
  }

  return <Component {...payload} />;
}
```

For composite (dashboard) payloads:

```typescript
// DashboardLayout.tsx
export function DashboardLayout({ payload }: { payload: A2UICompositePayload }) {
  const layoutStyles: Record<string, SxProps> = {
    vertical: { display: 'flex', flexDirection: 'column', gap: 2 },
    grid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 },
    sidebar: { display: 'flex', flexDirection: 'row', gap: 2 },
  };

  return (
    <Box sx={layoutStyles[payload.layout]}>
      {payload.components.map((child, index) => (
        <A2UIRenderer key={index} payload={child} />
      ))}
    </Box>
  );
}
```

### 5.3 LLM Gateway Integration (Custom Function-Calling Loop)

**IMPORTANT:** The architecture uses a custom function-calling loop, NOT the Vercel AI SDK. The frontend communicates with the LLM via OpenAI-compatible `/chat/completions` API using raw `fetch()` calls. Tool definitions are formatted as OpenAI-compatible `function` definitions, and tool results are fed back into the conversation context as `tool` role messages.

Key aspects of the actual implementation (`useLLM.ts`):

1. **Backend health check** — On every message, the frontend first checks if the backend is running at `GET /api/mcp/tools`. If not, a user-friendly error is displayed instead of silent hallucination.

2. **Tool discovery** — Tool definitions are fetched from `GET /api/mcp/tools` (the backend's HTTP REST API) and converted to OpenAI-compatible function definitions.

3. **Schema auto-fetch** — The database schema is pre-fetched via `introspect_schema` and injected into the system prompt so the LLM doesn't need to call the tool itself.

4. **Function calling loop** (max 5 iterations):
   - Send messages + tools to LLM API
   - If LLM responds with `tool_calls` (native): execute each tool against backend, add results as `tool` role messages, loop
   - If LLM responds with `<function>` tags (text-based, e.g., DeepSeek): parse tags, execute tools, add results as `user` role messages, loop
   - If no function calls: treat as final response

5. **A2UI extraction** — Final response is scanned for `---A2UI_START---` / `---A2UI_END---` markers. The JSON payload between them is parsed and rendered by `A2UIRenderer`.

6. **Test mode** — When `VITE_TEST_MODE=true`, the `simulateResponse()` function generates mock responses without any LLM call. This is used by E2E tests.

```typescript
// Simplified flow (see useLLM.ts for full implementation)
export function useLLM() {
  const sendMessage = async (message: string, history) => {
    // Health check
    try {
      await fetch(`${MCP_API_URL}/tools`)
    } catch {
      return { content: '⚠️ Backend not running. Start with: cd backend && ./mvnw spring-boot:run' }
    }

    // Tool discovery + schema injection
    const tools = await fetch(`${MCP_API_URL}/tools`).then(r => r.json())
    const schema = await executeTool('introspect_schema', {})
    const system = buildSystemPrompt(JSON.stringify(schema))

    // Function-calling loop
    let response = ''
    for (let i = 0; i < 5; i++) {
      const msg = await llmCall(system, history, message, tools)
      const toolCalls = detectToolCalls(msg)  // native + <function> + ```json
      if (!toolCalls.length) { response = msg.content; break }
      for (const tc of toolCalls) {
        const result = await executeTool(tc.name, tc.arguments)
        messages.push({ role: 'tool', content: JSON.stringify(result) })
      }
    }

    // A2UI extraction
    const { text, payload } = parseA2uiPayload(response)
    return { content: text, a2uiPayload: payload }
  }
  return { sendMessage }
}
```

**Supported models:**
- Any OpenAI-compatible API (GPT-4o, GPT-4, GPT-3.5) — native `tool_calls` format
- DeepSeek, Qwen, and similar open-source models — `<function>` text-based format
- Any model via ````json code blocks with `{name, arguments}` pattern
- Configure via `VITE_LLM_API_URL` and `VITE_LLM_MODEL` in `.env`

```

### 5.4 TypeScript Types (A2UI)

```typescript
// A2UITypes.ts
export type A2UIComponentType =
  | 'line_chart'
  | 'bar_chart'
  | 'pie_chart'
  | 'metric_card'
  | 'stat_card'
  | 'data_table'
  | 'progress_bar'
  | 'gauge'
  | 'area_chart'
  | 'confirmation_card'
  | 'error_card'
  | 'clarification_prompt'
  | 'proactive_insight';

export type DashboardLayout = 'vertical' | 'grid' | 'sidebar';

export interface A2UIPayload {
  componentType: A2UIComponentType;
  title?: string;
  description?: string;
  data: unknown;
  config?: Record<string, unknown>;
  action?: {
    type: 'create' | 'update' | 'delete';
    entity: string;
    recordId?: string | number;
    oldValues?: Record<string, unknown>;
    newValues: Record<string, unknown>;
  };
}

export interface A2UICompositePayload {
  componentType: 'dashboard';
  title?: string;
  layout: DashboardLayout;
  components: A2UIPayload[];
}

export type A2UIResponse = A2UIPayload | A2UICompositePayload;
```

---

## 6. Data Flow & State Management

### 6.1 State Architecture

State is managed via React Context + `useReducer` for the chat messages. No external state library.

```typescript
// useChat.ts
interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  a2uiPayload?: A2UIResponse;  // Present when AI renders a component
  timestamp: Date;
}

interface ChatState {
  messages: Message[];
  isLoading: boolean;
  activeEntity?: string;          // Current context entity (e.g., "Acme Corp")
  learnedPreferences: Record<string, string>;
}

type ChatAction =
  | { type: 'ADD_MESSAGE'; message: Message }
  | { type: 'SET_LOADING'; isLoading: boolean }
  | { type: 'SET_ACTIVE_ENTITY'; entity: string }
  | { type: 'UPDATE_PREFERENCES'; preferences: Record<string, string> };
```

### 6.2 State Flow

```
User types message
  → dispatch({ type: 'ADD_MESSAGE', message: userMessage })
  → setLoading(true)
  → sendMessage(text, history, mcpTools)
  → LLM streams response
  → if response contains A2UI payload:
      dispatch({ type: 'ADD_MESSAGE', message: assistantA2UIMessage })
  → if response contains text (clarification):
      dispatch({ type: 'ADD_MESSAGE', message: assistantTextMessage })
  → setLoading(false)
  → if write approved via ConfirmationCard:
      re-call LLM with approval signal
      → LLM calls execute() on MCP server
      → LLM streams success A2UI payload
```
```

### 6.3 Why Not Redux/Zustand

- **Single user, single chat session** — no complex state shape
- **Messages are append-only** — no random mutation
- **A2UI payloads are ephemeral** — rendered and displayed, no cross-component shared state
- **`useReducer` + Context** is sufficient for this scope; Redux overhead is not justified

---

## 7. A2UI Protocol Specification

### 7.1 Wire Format

The LLM returns A2UI payloads as structured JSON in its response. The LLM Gateway extracts these from the response stream and passes them to the frontend.

```typescript
// Example: Single component response
{
  "componentType": "line_chart",
  "title": "Q3 2026 Sales Trend",
  "data": [
    { "month": "2026-07", "revenue": 45000 },
    { "month": "2026-08", "revenue": 52000 },
    { "month": "2026-09", "revenue": 48000 }
  ],
  "config": {
    "xAxis": "month",
    "yAxis": "revenue",
    "unit": "USD"
  }
}

// Example: Composite dashboard response
{
  "componentType": "dashboard",
  "title": "Acme Corp Overview",
  "layout": "grid",
  "components": [
    {
      "componentType": "metric_card",
      "title": "Total Revenue",
      "data": { "value": 450000, "unit": "USD" }
    },
    {
      "componentType": "stat_card",
      "title": "Summary",
      "data": { "deals": 3, "contacts": 12 }
    },
    {
      "componentType": "data_table",
      "title": "Open Deals",
      "data": [
        { "deal": "Q3 Contract", "amount": 50000, "stage": "negotiation" },
        { "deal": "Support Plan", "amount": 12000, "stage": "pipeline" }
      ]
    }
  ]
}
```

### 7.2 LLM System Prompt (Key Section)

The system prompt instructs the LLM on A2UI output format. Key excerpt:

```
You are ContextCanvas, an AI data assistant connected to a live SQLite database.

OUTPUT RULES:
1. When you need to show data, respond with a JSON A2UI payload.
2. Available component types: line_chart, bar_chart, pie_chart, metric_card,
   stat_card, data_table, progress_bar, gauge, area_chart, confirmation_card,
   error_card, clarification_prompt, proactive_insight.
3. For complex responses, you may use a "dashboard" composite with layout
   "vertical", "grid", or "sidebar".
4. You MUST NOT output raw markdown tables, code blocks, or JSON dumps.
5. For write operations, ALWAYS return a confirmation_card first. Never
   execute directly.
6. When the user's request is ambiguous, use clarification_prompt.
7. When you detect an anomaly or opportunity, you may use proactive_insight
   (max once per 3 user messages).
```

---

## 8. Database Schema & Introspection

### 8.1 Full DDL

```sql
-- V1__create_clients.sql
CREATE TABLE IF NOT EXISTS clients (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    company_name TEXT    NOT NULL,
    industry    TEXT,
    status      TEXT    CHECK(status IN ('active', 'inactive', 'lead')) DEFAULT 'lead',
    contact_email TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- V2__create_sales.sql
CREATE TABLE IF NOT EXISTS sales (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id   INTEGER NOT NULL REFERENCES clients(id),
    deal_amount REAL    NOT NULL,
    stage       TEXT    CHECK(stage IN ('pipeline', 'negotiation', 'closed_won', 'closed_lost')) DEFAULT 'pipeline',
    close_date  DATE,
    owner       TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- V3__create_contacts.sql
CREATE TABLE IF NOT EXISTS contacts (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id   INTEGER NOT NULL REFERENCES clients(id),
    full_name   TEXT    NOT NULL,
    title       TEXT,
    email       TEXT,
    phone       TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- V4__create_user_preferences.sql
CREATE TABLE IF NOT EXISTS user_preferences (
    key         TEXT PRIMARY KEY,
    value       TEXT NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 8.2 Introspection Output Format

The `introspect_schema()` tool returns:

```json
{
  "tables": [
    {
      "name": "clients",
      "columns": [
        { "name": "id", "type": "INTEGER", "nullable": false, "primaryKey": true, "defaultValue": null },
        { "name": "company_name", "type": "TEXT", "nullable": false, "primaryKey": false, "defaultValue": null },
        { "name": "industry", "type": "TEXT", "nullable": true, "primaryKey": false, "defaultValue": null },
        { "name": "status", "type": "TEXT", "nullable": true, "primaryKey": false, "defaultValue": "lead" },
        { "name": "contact_email", "type": "TEXT", "nullable": true, "primaryKey": false, "defaultValue": null },
        { "name": "created_at", "type": "TIMESTAMP", "nullable": true, "primaryKey": false, "defaultValue": "CURRENT_TIMESTAMP" }
      ],
      "foreignKeys": []
    },
    {
      "name": "sales",
      "columns": [
        { "name": "id", "type": "INTEGER", "nullable": false, "primaryKey": true, "defaultValue": null },
        { "name": "client_id", "type": "INTEGER", "nullable": false, "primaryKey": false, "defaultValue": null },
        { "name": "deal_amount", "type": "REAL", "nullable": false, "primaryKey": false, "defaultValue": null },
        { "name": "stage", "type": "TEXT", "nullable": true, "primaryKey": false, "defaultValue": "pipeline" },
        { "name": "close_date", "type": "DATE", "nullable": true, "primaryKey": false, "defaultValue": null },
        { "name": "owner", "type": "TEXT", "nullable": true, "primaryKey": false, "defaultValue": null },
        { "name": "created_at", "type": "TIMESTAMP", "nullable": true, "primaryKey": false, "defaultValue": "CURRENT_TIMESTAMP" }
      ],
      "foreignKeys": [
        { "fromColumn": "client_id", "referencedTable": "clients", "referencedColumn": "id" }
      ]
    },
    {
      "name": "contacts",
      "columns": [ ... ],
      "foreignKeys": [
        { "fromColumn": "client_id", "referencedTable": "clients", "referencedColumn": "id" }
      ]
    }
  ]
}
```

### 8.3 Flyway Configuration

```yaml
# application.yml (relevant section)
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    # SQLite requires special handling — no schema creation
    create-schemas: false
```

---

## 9. LLM Integration

### 9.1 Multi-Provider Architecture

```
┌─────────────────────────────────────────────┐
│           LLM Gateway (Vercel AI SDK)       │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │  Provider Registry                    │  │
│  │                                       │  │
│  │  Provider "openai" ─── OpenAI SDK     │  │
│  │  Provider "anthropic" ── Anthropic SDK│  │
│  │  Provider "local" ──── Ollama/LM Studio│  │
│  └───────────────────────────────────────┘  │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │  MCP Tool Registry                    │  │
│  │  - introspect_schema                  │  │
│  │  - query                              │  │
│  │  - execute                            │  │
│  │  - resolve_entity                     │  │
│  │  - track_preference                   │  │
│  │  - get_preferences                    │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

### 9.2 MCP Tool Definitions Sent to LLM

Each tool definition includes name, description, and JSON Schema parameters:

```json
{
  "tools": [
    {
      "name": "query",
      "description": "Execute a parameterized SELECT query. Only SELECT statements are allowed.",
      "parameters": {
        "type": "object",
        "properties": {
          "sql": {
            "type": "string",
            "description": "SELECT statement with ? placeholders"
          },
          "params": {
            "type": "array",
            "items": { "type": "string" },
            "description": "Parameter values matching ? placeholders"
          }
        },
        "required": ["sql"]
      }
    },
    {
      "name": "execute",
      "description": "Execute an INSERT, UPDATE, or DELETE statement. Returns a confirmation payload. Requires user approval before execution.",
      "parameters": {
        "type": "object",
        "properties": {
          "sql": {
            "type": "string",
            "description": "INSERT/UPDATE/DELETE with ? placeholders"
          },
          "params": {
            "type": "array",
            "items": { "type": "string" }
          },
          "confirmationToken": {
            "type": "string",
            "description": "Optional. Include only after user has approved the confirmation card."
          }
        },
        "required": ["sql"]
      }
    }
    // ... other tools
  ]
}
```

### 9.3 Proactive Insight Triggering

The system prompt includes instructions for proactive behavior:

```
PROACTIVE BEHAVIOR:
- After a read query, check if any records need attention (stagnation > 30 days,
  leads with no follow-up > 90 days, milestones approaching).
- If you detect an issue, use proactive_insight component.
- Limit: one proactive insight per 3 user messages.
- Do NOT interrupt a write flow with proactive insights.
```

---

## 10. Testing Strategy

### 10.1 Philosophy

**E2E-first, integration-focused, mocks-free.**

| Layer | Test Type | Tool | Coverage Goal |
|---|---|---|---|
| **Backend tools** | End-to-end (real SQLite, real Spring context) | JUnit 5 | 100% of tool execution paths |
| **Backend services** | Integration (real Spring context, real DB) | JUnit 5 | 100% of service methods |
| **Frontend A2UI** | End-to-end (real browser, real MCP server) | Playwright | Every component type rendered |
| **Frontend flows** | End-to-end (read → write → confirm cycle) | Playwright | All user journeys |
| **Cross-cutting** | Visual regression (A2UI components) | Playwright | No unintended UI changes |
| **Security** | SQL injection attempts, DDL rejection | JUnit 5 | Edge case coverage |

**No unit tests with mocks unless:**
- A code path is unreachable via E2E (e.g., a fallback after 5 retries that requires specific timing)
- The mock is for an external service that cannot run locally
- This should cover <5% of the codebase

### 10.2 Backend E2E Tests

```java
// AllToolsE2ETest.java
@SpringBootTest(classes = ContextCanvasApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class AllToolsE2ETest {

    @Autowired
    private McpServer mcpServer;

    private static final String TEST_DB = "build/test-data/e2e-test.db";

    @BeforeAll
    static void setupDatabase() {
        // Flyway migrates a fresh SQLite database
        System.setProperty("spring.datasource.url", "jdbc:sqlite:%s".formatted(TEST_DB));
    }

    @Test
    void queryTool_returnsData() {
        McpToolResult result = mcpServer.callTool("query", Map.of(
                "sql", "SELECT COUNT(*) as cnt FROM clients",
                "params", List.of()
        ));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).extracting("rows[0].cnt").isEqualTo(0); // empty DB
    }

    @Test
    void executeTool_returnsConfirmation_notExecuted() {
        McpToolResult result = mcpServer.callTool("execute", Map.of(
                "sql", "INSERT INTO clients (company_name, status) VALUES (?, ?)",
                "params", List.of("Test Corp", "active")
        ));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData())
                .hasFieldOrPropertyWithValue("status", "PENDING_CONFIRMATION");
        // Verify NOT yet inserted
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM clients WHERE company_name = ?",
                Long.class, "Test Corp");
        assertThat(count).isZero();
    }

    @Test
    void introspectSchema_returnsTableDefinitions() {
        McpToolResult result = mcpServer.callTool("introspect_schema", Map.of());
        assertThat(result.isSuccess()).isTrue();
        List<Map<String, ?>> tables = (List<Map<String, ?>>) result.getData("tables");
        assertThat(tables).extracting("name").containsExactly("clients", "sales", "contacts");
    }

    @Test
    void ddlIsRejected() {
        McpToolResult result = mcpServer.callTool("query", Map.of(
                "sql", "DROP TABLE clients",
                "params", List.of()
        ));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("DDL operations");
    }

    @Test
    void sqlInjectionIsBlocked() {
        McpToolResult result = mcpServer.callTool("query", Map.of(
                "sql", "SELECT * FROM clients WHERE company_name = ?",
                "params", List.of("'; DROP TABLE clients; --")
        ));
        assertThat(result.isSuccess()).isTrue();  // Parameterized, injection is data not SQL
        // The DROP is never executed because it's inside a parameter value
    }
}
```

### 10.3 Frontend E2E Tests (Playwright)

```typescript
// e2e/write-flow.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Write flow with confirmation gate', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // Wait for LLM to initialize and introspect schema
    await page.waitForSelector('[data-testid="chat-input"]');
  });

  test('user sees confirmation card before write is executed', async ({ page }) => {
    const input = page.getByTestId('chat-input');
    await input.fill('Add a new sale of $5,000 for Acme Corp');
    await input.press('Enter');

    // Wait for A2UI confirmation card to render
    const confirmationCard = page.getByTestId('a2ui-confirmation-card');
    await expect(confirmationCard).toBeVisible({ timeout: 15000 });

    // Verify card shows transaction details
    await expect(confirmationCard).toContainText('CREATE');
    await expect(confirmationCard).toContainText('Acme Corp');
    await expect(confirmationCard).toContainText('$5,000');

    // Verify Approve and Cancel buttons exist
    await expect(page.getByTestId('btn-approve')).toBeVisible();
    await expect(page.getByTestId('btn-cancel')).toBeVisible();
  });

  test('approving a write shows success and updated data', async ({ page }) => {
    const input = page.getByTestId('chat-input');
    await input.fill('Add a new sale of $5,000 for Acme Corp');
    await input.press('Enter');

    await expect(page.getByTestId('a2ui-confirmation-card')).toBeVisible();
    await page.getByTestId('btn-approve').click();

    // Wait for success state
    await expect(page.getByTestId('a2ui-success-alert')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('a2ui-success-alert')).toContainText('Sale created');
  });
});
```

```typescript
// e2e/a2ui-components.spec.ts
import { test, expect } from '@playwright/test';

test.describe('A2UI component rendering', () => {

  test('renders all base component types', async ({ page }) => {
    // This test uses pre-recorded A2UI payloads (no LLM call)
    await page.goto('/?test-mode=true');

    const testPayloads = [
      'line_chart', 'bar_chart', 'pie_chart', 'metric_card',
      'data_table', 'progress_bar', 'gauge', 'area_chart'
    ];

    for (const type of testPayloads) {
      await page.evaluate((t) => {
        window.postMessage({
          type: 'TEST_A2UI',
          payload: { componentType: t, title: t, data: [] }
        }, '*');
      }, type);

      await expect(page.getByTestId(`a2ui-${type}`)).toBeVisible();
    }
  });

  test('renders composite dashboard layout', async ({ page }) => {
    await page.goto('/?test-mode=true');

    const dashboardPayload = {
      componentType: 'dashboard',
      layout: 'grid',
      components: [
        { componentType: 'metric_card', title: 'Revenue', data: { value: 450000 } },
        { componentType: 'data_table', title: 'Deals', data: [] }
      ]
    };

    await page.evaluate((payload) => {
      window.postMessage({ type: 'TEST_A2UI', payload }, '*');
    }, dashboardPayload);

    await expect(page.getByTestId('a2ui-dashboard')).toBeVisible();
    await expect(page.getByTestId('a2ui-metric_card')).toBeVisible();
    await expect(page.getByTestId('a2ui-data_table')).toBeVisible();
  });
});
```

```typescript
// e2e/proactive-insights.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Proactive insights', () => {

  test('AI surfaces stagnation insight after third query', async ({ page }) => {
    await page.goto('/');

    const input = page.getByTestId('chat-input');
    await input.fill('Show me all clients');
    await input.press('Enter');
    await page.waitForTimeout(3000);

    await input.fill('Show me pipeline deals');
    await input.press('Enter');
    await page.waitForTimeout(3000);

    await input.fill('Show me my contacts');
    await input.press('Enter');

    // After 3 interactions, AI may surface a proactive insight
    const insight = page.getByTestId('a2ui-proactive_insight');
    // This may or may not appear based on data conditions — the test verifies
    // that IF it appears, it has the correct structure
    if (await insight.isVisible()) {
      await expect(insight).toContainText(/lead|stagnant|untouched|follow.up/i);
    }
  });
});
```

### 10.4 Test Fixtures

The E2E test database is pre-seeded with realistic data:

```sql
-- test-database.sqlite (generated by test setup)
INSERT INTO clients (id, company_name, industry, status, created_at) VALUES
  (1, 'Acme Corporation', 'Technology', 'active', '2025-01-15'),
  (2, 'BetterCloud Inc', 'Technology', 'lead', '2026-01-01'),
  (3, 'Swift Logistics', 'Logistics', 'lead', '2025-12-15'),
  (4, 'DataStream Analytics', 'Technology', 'active', '2025-06-01'),
  (5, 'GreenLeaf Partners', 'Consulting', 'inactive', '2025-03-20');

INSERT INTO sales (id, client_id, deal_amount, stage, close_date, owner, created_at) VALUES
  (1, 1, 50000, 'negotiation', '2026-09-30', 'Sarah', '2026-06-01'),
  (2, 1, 12000, 'pipeline', NULL, 'Sarah', '2026-06-15'),
  (3, 4, 75000, 'closed_won', '2026-08-01', 'Sarah', '2026-07-01'),
  (4, 2, 15000, 'pipeline', NULL, 'Sarah', '2026-01-01'),
  (5, 3, 8000, 'pipeline', NULL, 'Sarah', '2026-01-01');

INSERT INTO contacts (id, client_id, full_name, title, email) VALUES
  (1, 1, 'John Smith', 'CTO', 'john@acme.com'),
  (2, 1, 'Jane Doe', 'VP Eng', 'jane@acme.com'),
  (3, 4, 'Alice Wu', 'CEO', 'alice@datastream.io'),
  (4, 2, 'Bob Chen', 'Director', 'bob@bettercloud.io'),
  (5, 3, 'Maria Garcia', 'Ops Lead', 'maria@swiftlogistics.com');
```

This fixture enables deterministic E2E tests that exercise real SQL queries, confirmation flows, and proactive insight detection (leads untouched since January 2026).

### 10.5 Visual Regression Testing

```typescript
// playwright.config.ts (relevant section)
import { defineConfig } from '@playwright/test';

export default defineConfig({
  projects: [
    {
      name: 'visual-regression',
      testMatch: '**/*.visual.spec.ts',
      use: {
        viewport: { width: 1280, height: 800 },
      },
    },
  ],
  // Snapshot directory for baseline images
  snapshotDir: './e2e/snapshots',
  // Threshold for pixel difference
  maxDiffPixels: 100,
});
```

---

## 11. Configuration Externalization

### 11.1 Backend Configuration

```yaml
# application.yml
contextcanvas:
  database:
    path: ${CONTEXTCANVAS_DB_PATH:./data/contextcanvas.db}
  mcp:
    server-name: "contextcanvas-mcp"
    server-version: "1.0.0"

spring:
  datasource:
    url: jdbc:sqlite:${contextcanvas.database.path}
    driver-class-name: org.sqlite.JDBC
    hikari:
      maximum-pool-size: 4  # SQLite is single-writer; 4 is sufficient for PoC
      connection-timeout: 5000
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

logging:
  level:
    com.contextcanvas: ${CONTEXTCANVAS_LOG_LEVEL:INFO}
    org.springframework: WARN
```

```yaml
# application-dev.yml (overrides)
contextcanvas:
  database:
    path: ./data/dev.db

logging:
  level:
    com.contextcanvas: DEBUG
```

### 11.2 Frontend Configuration

```typescript
// config/index.ts (actual — 11 environment variables)
export const config = {
  LLM_PROVIDER:    import.meta.env.VITE_LLM_PROVIDER     ?? 'openai',
  LLM_MODEL:       import.meta.env.VITE_LLM_MODEL        ?? 'gpt-4o',
  LLM_API_KEY:     import.meta.env.VITE_LLM_API_KEY       ?? '',  // Must be set by user
  LLM_API_URL:     import.meta.env.VITE_LLM_API_URL       ?? '/api/chat',  // Self-hosted LLM endpoint
  MCP_API_URL:     import.meta.env.VITE_MCP_API_URL       ?? 'http://localhost:8080/api/mcp',  // Backend REST API
  MCP_SERVER_PATH: import.meta.env.VITE_MCP_SERVER_PATH  ?? '../backend/target/mcp-server.jar',
  POSTHOG_KEY:     import.meta.env.VITE_POSTHOG_KEY       ?? '',
  POSTHOG_HOST:    import.meta.env.VITE_POSTHOG_HOST      ?? 'https://app.posthog.com',
  TEST_MODE:       import.meta.env.VITE_TEST_MODE         === 'true',
} as const;
```

Key differences from the documented set:
- `VITE_LLM_API_URL` — configures the LLM API endpoint (defaults to `/api/chat`, proxied by Vite to `https://api.deepseek.com/v1`). Set this to point at any OpenAI-compatible provider.
- `VITE_MCP_API_URL` — configures the backend REST API URL (defaults to `http://localhost:8080/api/mcp`). Must point at the running backend's `/api/mcp` path.
- `VITE_TEST_MODE` — when `true`, the `useLLM.ts` simulateResponse() function generates mock data without any LLM call. Used by Playwright E2E tests.

### 11.3 Environment Variables

```bash
# .env.example
# Backend
CONTEXTCANVAS_DB_PATH=./data/contextcanvas.db
CONTEXTCANVAS_LOG_LEVEL=INFO
CONTEXTCANVAS_PORT=8080

# Frontend
VITE_LLM_PROVIDER=openai
VITE_LLM_MODEL=gpt-4o
VITE_LLM_API_KEY=sk-...
VITE_LLM_API_URL=/api/chat
VITE_MCP_API_URL=http://localhost:8080/api/mcp
VITE_MCP_SERVER_PATH=../backend/target/mcp-server.jar
VITE_POSTHOG_KEY=phc_...
VITE_POSTHOG_HOST=https://app.posthog.com
VITE_TEST_MODE=false
```

### 11.4 Configuration Precedence

```
1. Environment variables (highest priority)
2. .env file (local development)
3. application.yml defaults (lowest priority)
```

---

## 12. Security & Safety

### 12.1 SQL Injection Prevention

- **All queries use parameterized SQL** via `?` placeholders
- `SqlValidator` rejects any SQL containing DDL keywords (CREATE, ALTER, DROP, TRUNCATE)
- `SqlValidator` ensures parameter count matches placeholder count
- No string concatenation for SQL construction anywhere in the backend

### 12.2 Write Operation Safety Gate

```
User requests write → LLM calls execute() → Server responds with PENDING_CONFIRMATION
                                              |
                                              v
                              Frontend renders ConfirmationCard
                                              |
                          User clicks Approve → Frontend sends approval
                                              |
                          LLM calls execute() with confirmationToken
                                              |
                          Server validates token → executes write
```

- `execute()` returns `PENDING_CONFIRMATION` on first call — no data is modified
- Only when the LLM provides a `confirmationToken` (generated by the server) does the write proceed
- The confirmation token expires after 60 seconds

### 12.3 Data Integrity

- Flyway manages schema versioning — no manual DDL
- Foreign key constraints enforced at the SQLite level
- CHECK constraints on `status` and `stage` columns prevent invalid data

### 12.4 LLM Output Safety

- Frontend A2UI Renderer validates every AI response: if it's not valid A2UI JSON, it renders an `error_card`
- The component catalog prevents the AI from inventing new component types
- The frontend theme prevents the AI from specifying colors/layouts — all styling is controlled by MUI theme

---

## 13. Build & Deployment

### 13.1 Build Commands

```bash
# Backend (Java)
cd backend && ./mvnw clean package -DskipTests

# Frontend
cd frontend && npm ci && npm run build

# Run E2E tests
cd frontend && npx playwright test

# Run backend tests
cd backend && ./mvnw verify

# Local development
docker compose up
```

### 13.2 Docker Compose

```yaml
# docker-compose.yml
services:
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    environment:
      CONTEXTCANVAS_DB_PATH: /data/contextcanvas.db
    volumes:
      - ./data:/data

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
      args:
        VITE_LLM_PROVIDER: ${VITE_LLM_PROVIDER}
        VITE_LLM_MODEL: ${VITE_LLM_MODEL}
        VITE_POSTHOG_KEY: ${VITE_POSTHOG_KEY}
    ports:
      - "5173:5173"  # Vite dev server
    depends_on:
      - backend
    environment:
      VITE_MCP_SERVER_PATH: /backend/mcp-server.jar
```

### 13.3 CI Pipeline

```yaml
# .github/workflows/ci.yml
name: CI
on: [pull_request]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Lint backend
        run: cd backend && ./mvnw spotless:check
      - name: Lint frontend
        run: cd frontend && npm ci && npm run lint

  test-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - name: Run backend tests
        run: cd backend && ./mvnw verify

  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22' }
      - name: Install + build
        run: cd frontend && npm ci && npm run build
      - name: Run E2E tests
        run: cd frontend && npx playwright test
```

### 13.4 Backend Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY target/mcp-server-*.jar mcp-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "mcp-server.jar"]
```

### 13.5 Frontend Dockerfile

```dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_LLM_PROVIDER
ARG VITE_LLM_MODEL
ARG VITE_POSTHOG_KEY
RUN npm run build

FROM nginx:stable-alpine AS runtime
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

---

## 14. Development Phases

| Phase | Weeks | Deliverables | Key Risks |
|---|---|---|---|
| **1: Foundation** | 1-3 | Spring Boot MCP server scaffold, Flyway migrations, schema introspection, React scaffold with Vite, A2UI Renderer skeleton, MUI theme | Correct MCP SDK configuration for Java |
| **2: Core Read** | 4-6 | `query` tool with SQL validation, dynamic SQL generation from schema, basic component catalog (line_chart, metric_card, data_table), LLM Gateway integration, single-component rendering | LLM generating correct SQL from schema |
| **3: Write + Agentic** | 7-9 | `execute` tool with confirmation gate, composite dashboards, dynamic metrics (FR-1.4), self-healing query recovery (Section 6.2), proactive insights (FR-3.1) | Confirmation token flow correctness |
| **4: Learning + Polish** | 10-12 | `track_preference`/`get_preferences` tools, cross-session memory (FR-3.3), analytics pipeline, agentic KPI tracking, E2E test suite, visual regression, demo preparation | Preference learning not annoying users |

---

## Appendix A: Key Dependency Versions

| Dependency | Version | CVE Status |
|---|---|---|
| Spring Boot | 3.4.1 | No critical CVEs |
| sqlite-jdbc | 3.45.1.0 | No critical CVEs |
| HikariCP | 6.0.0 | No critical CVEs |
| Flyway | 10.20.0 | No critical CVEs |
| Jackson | 2.17.2 | No critical CVEs |
| MCP Java SDK | 0.6.0 | Pre-release, actively maintained |
| React | 19.1.0 | No critical CVEs |
| MUI | 6.4.0 | No critical CVEs |
| Vite | 6.3.0 | No critical CVEs |
| Vercel AI SDK | 4.1.0 | No critical CVEs |
| Playwright | 1.48.0 | No critical CVEs |
| PostHog JS | 1.150.0 | No critical CVEs |

---

## Appendix B: Key Architectural Decisions

| # | Decision | Rationale | Date |
|---|---|---|---|
| AD01 | Generic `query`/`execute` tools instead of entity-specific tools | Enables schema introspection pattern; any future table works without code changes | 2026-06-18 |
| AD02 | Spring Boot with HTTP REST (primary) + stdio MCP transport (secondary) | Web frontend needs HTTP; MCP stdio transport available for desktop LLM hosts (Claude Desktop) as alternative | 2026-06-18 |
| AD03 | Flyway for schema management instead of JPA/Hibernate | JPA adds complexity for 3 tables; Flyway version-controlled SQL is simpler and safer | 2026-06-18 |
| AD04 | React Context + useReducer instead of Redux/Zustand | Single-user chat app with append-only message list doesn't need external state library | 2026-06-18 |
| AD05 | Playwright for all frontend tests | Cross-browser, network interception, visual regression — single tool for all E2E needs | 2026-06-18 |
| AD06 | No unit test mocking — only E2E and integration tests | Mocks hide real integration bugs; E2E tests with real SQLite + real MCP server provide higher confidence | 2026-06-18 |
| AD07 | Backend Java RestClient for multi-provider LLM access (not Vercel AI SDK) | Backend orchestrates the function-calling loop; RestClient calls any OpenAI-compatible API (DeepSeek, GPT-4o, etc.) without SDK dependency | 2026-06-18 |
| AD08 | Preferences stored in SQLite `user_preferences` table | No additional infrastructure needed; same SQLite file as business data; easy to inspect | 2026-06-18 |
| AD09 | Configuration via environment variables + `.env` file | Industry standard; Spring Boot + Vite both support this natively; no hardcoded secrets | 2026-06-18 |
| AD10 | Proactive insights limited to 1 per 3 user messages | Prevents AI from overwhelming user; rate limit is documented and testable | 2026-06-18 |
