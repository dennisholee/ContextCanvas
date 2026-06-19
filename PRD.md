# Product Requirements Document (PRD)

## Product Name: ContextCanvas (AI-Powered Workspace PoC)

**Document Version:** 2.1 (Core Features — Schema Introspection, A2UI Rendering)
**Product Manager:** AI Product Specialist
**Status:** Approved for PoC Development
**Last Updated:** 2026-06-19

---

## Table of Contents

1. [Product Vision & Value Proposition](#1-product-vision--value-proposition)
2. [User Personas & Core Journeys](#2-user-personas--core-journeys)
3. [Technical Architecture & Scope Boundaries](#3-technical-architecture--scope-boundaries)
4. [Product Feature & Functional Requirements](#4-product-feature--functional-requirements)
5. [A2UI Component Catalog](#5-a2ui-component-catalog)
6. [User Experience & Design Guardrails](#6-user-experience--design-guardrails)
7. [Success Metrics](#7-success-metrics)
8. [Development Scope & Out-of-Scope](#8-development-scope--out-of-scope)

---

## 1. Product Vision & Value Proposition

### 1.1 Product Vision

ContextCanvas transforms traditional conversational AI from a static text-and-markdown chat window into a fluid, adaptive graphical user interface built on an **agent-driven UI protocol (A2UI)**. By connecting an AI assistant directly to live business databases, ContextCanvas reads and writes business data in real time, displaying information via native, clickable dashboard components instead of walls of text. The AI agent has full autonomy over component selection, data structuring, and visual orchestration, constrained only by a pre-approved component catalog. The AI **discovers the data model dynamically** and generates SQL queries based on its schema understanding.

### 1.2 Core Value Proposition (Implemented)

- **Zero Learning Curve:** Business users can manage, analyze, and update database records using plain language.
- **Agentic Conversations:** The AI interprets intent, resolves entities, selects optimal visualization components autonomously.
- **Self-Discovering Data Model:** The AI introspects the database schema at startup and dynamically understands tables, columns, and relationships.
- **Actionable Conversations:** The chat interface updates and visualizes data instantly inside the stream.
- **Human-in-the-Loop Safety:** Users maintain total control with explicit, interactive confirmation steps before any business data is modified.

### 1.3 Future Value Propositions (Not Yet Implemented)

- **Learning Across Sessions:** The AI learns user preferences and behavior patterns over time.
- **Proactive Insights:** The AI surfaces trends and anomalies without being asked.
- **Self-Healing Queries:** The AI retries failed queries with fuzzy matching before surfacing errors.

---

## 2. User Personas & Core Journeys

### 2.1 Primary Persona: Operational Business User

- **Profile:** Sarah, Operations Manager at a fast-growing B2B company.
- **Pain Point:** Frustrated by navigating multiple legacy database views and dashboards.

### 2.2 Core Product User Journeys

```
[ Session Start ] --------> [ AI introspects database schema ]
                                      |
                                      v
[ User asks for Data ] --------> [ AI generates SQL via schema understanding ]
                                        |
                                        v
                    [ Backend executes query against SQLite ]
                                        |
                                        v
                    [ LLM determines best A2UI component ]
                                        |
                                        v
                  [ Chat renders Interactive Component ]
                    (line chart / table / metric card / composite dashboard)
                                        |
[ User requests Data Change ]           |
                                        v
                    [ AI resolves entities & enriches data ]
                                        |
                                        v
                    [ AI surfaces Full Transparency Confirmation Card ]
                      (old/new values, record detail,
                       Approve + Cancel buttons)
                                        |
[ User clicks "Approve" ] <-------------|
        |
        v
[ Backend executes DB operation ]
[ UI reflects updated state in real time ]
```

### 2.3 Agentic Journey Extensions (Implemented)

- **Multi-Component Dashboards:** The AI may compose a full dashboard in one response — e.g., a `metric_card` summary + `data_table` + `line_chart`.
- **Ambiguity Handling:** The AI asks clarifying questions when the user's request is ambiguous.

### 2.4 Agentic Journey Extensions (Not Yet Implemented)

- **Dynamic Metrics:** Natural language metric definitions.
- **Self-Healing Queries:** Fuzzy match recovery before errors.
- **Proactive Insights:** AI-initiated observations.
- **Contextual Memory:** Cross-session preference learning.

---

## 3. Technical Architecture & Scope Boundaries

### 3.1 Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                     Browser (Desktop Web)                        │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │            React + TypeScript + Vite                      │   │
│  │                                                           │   │
│  │  ┌─────────┐  ┌────────────┐  ┌─────────────────────┐   │   │
│  │  │ Chat UI │  │ A2UI       │  │ useLLM (thin POST   │   │   │
│  │  │ (MUI)   │  │ Renderer   │  │  client to backend) │   │   │
│  │  └─────────┘  │ (Component │  └─────────────────────┘   │   │
│  │               │  Catalog)  │                             │   │
│  │               └────────────┘                             │   │
│  └──────────────────────┬───────────────────────────────────┘   │
│                         │ POST /api/chat                        │
└─────────────────────────┼───────────────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────────────┐
│  Java Backend (Spring Boot)                                     │
│                         ▼                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ChatController│  │LlmChatService│  │ ToolDispatcher       │   │
│  │ (POST /api/  │→ │(function-    │→ │ (in-process dispatch)│   │
│  │  chat)       │  │ calling loop)│  │ - query / execute    │   │
│  └─────────────┘  └──────┬───────┘  │ - introspect_schema  │   │
│                          │          │ - resolve_entity      │   │
│                          │ HTTP     │ - preferences / etc.  │   │
│                          ▼          └───────────┬───────────┘   │
│               ┌──────────────────────┐          │               │
│               │ LLM Provider         │          ▼               │
│               │ (OpenAI-compatible   │  ┌──────────────────┐    │
│               │  API via RestClient) │  │ SQLite (sqlite-  │    │
│               └──────────────────────┘  │ jdbc)            │    │
│                                         │ clients / sales  │    │
│                                         │ contacts / pref  │    │
│                                         └──────────────────┘    │
│                                                                  │
│  Stdio Transport (available for desktop MCP hosts):              │
│  ┌────────────────────────────────────────────────────────┐     │
│  │ McpServer (stdio JSON-RPC) — for Claude Desktop/VS Code│     │
│  └────────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────┘
```

**Data Flow:**
1. Browser → POST /api/chat → ChatController → LlmChatService
2. LlmChatService → POST /chat/completions → LLM with tool definitions
3. LLM responds with tool_calls (OpenAI-compatible format)
4. LlmChatService executes tools via ToolDispatcher → SQLite
5. Results fed back to LLM → loop until final answer
6. LLM emits A2UI payload in response → extracted by backend → returned to frontend

### 3.2 Database Schema (PoC)

```sql
CREATE TABLE clients (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_name TEXT NOT NULL, industry TEXT,
    status TEXT CHECK(status IN ('active','inactive','lead')) DEFAULT 'lead',
    contact_email TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sales (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id INTEGER NOT NULL REFERENCES clients(id),
    deal_amount REAL NOT NULL,
    stage TEXT CHECK(stage IN ('pipeline','negotiation','closed_won','closed_lost')) DEFAULT 'pipeline',
    close_date DATE, owner TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE contacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id INTEGER NOT NULL REFERENCES clients(id),
    full_name TEXT NOT NULL, title TEXT, email TEXT, phone TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_preferences (
    key TEXT PRIMARY KEY, value TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.3 AI-to-Database Connectivity

- **Channel:** Java backend orchestrates function-calling loop via HTTP to OpenAI-compatible API
- **Tool Definitions:** 6 tools defined via custom `McpTool` interface, registered in `ToolDispatcher`
- **Tools:**
  - `introspect_schema()` — Returns complete table definitions
  - `query(sql, params)` — Parameterized SELECT statements
  - `execute(sql, params)` — Parameterized INSERT/UPDATE/DELETE with confirmation gate
  - `resolve_entity(name, table)` — Fuzzy/LIKE matching
  - `track_preference(key, value)` — Persists user preference
  - `get_preferences()` — Returns all saved preferences
- **Transport:** HTTP REST (primary) + stdio MCP (available for desktop hosts)
- **Safety:** All write operations intercepted at the tool level, require user approval

### 3.4 LLM Provider

- **Multi-provider support** via configurable endpoint URL
- **Tested with:** DeepSeek (OpenAI-compatible API)
- **Also compatible with:** Any OpenAI-compatible API
- **LLM client:** Java `RestClient` — no SDK dependency

### 3.5 Multi-User Considerations

- Single-user PoC only. Multi-tenancy out of scope.

---

## 4. Product Feature & Functional Requirements

### 4.1 Feature Group A: Agentic Data Visualization (Read Experience)

#### FR-1.1: Automatic Layout Switching via A2UI

**Description:** The AI agent must autonomously select and configure the optimal UI component.

**Mechanism:** The AI emits a structured A2UI payload. The frontend renders the corresponding component.

**Behavior (implemented):**
- Trend queries → `line_chart` component
- Summary queries → `metric_card` component
- List queries → `data_table` component
- Distribution queries → `bar_chart` or `pie_chart` component
- Overview queries → `dashboard` composite with layout

**Constraints:**
- AI MUST NOT invent new component types
- AI MUST NOT fall back to raw markdown tables for data

#### FR-1.2: Interactive Data Tables

**Description:** Data lists rendered as native MUI tables.

**Behavior:**
- Structured columns within chat stream
- Scrollable, column sorting, row hover states

#### FR-1.3: Full Dashboard Visualization Suite

**Description:** Supports line charts, bar charts, pie charts, metric cards, data tables, stat cards, progress bars, gauges, area charts, confirmation cards, error cards, clarification prompts, proactive insights, and composite dashboards.

#### FR-1.4: Dynamic Derived Metrics (Not Yet Implemented)

**Description:** AI interprets natural language metric definitions. *Scheduled for Phase 3.*

#### FR-1.5: Multi-Component Dashboard Composition

**Description:** AI emits composite A2UI payload with multiple components in a layout.

**Supported layouts:** `vertical`, `grid`, `sidebar`

### 4.2 Feature Group B: Conversational Data Entry & Updates

#### FR-2.1: Human-in-the-Loop Confirmation Gate

**Description:** Write operations are intercepted with a confirmation card.

**Behavior:**
- AI resolves entities (e.g., "Acme" → "Acme Corporation")
- Surfaces confirmation card with action type, entity, field details
- [✓ Approve] and [✗ Cancel] buttons
- Execution only proceeds on user approval
- Confirmation token expires after 60 seconds

#### FR-2.2: Live State Synchronization

**Description:** UI reflects database changes immediately after approval.

### 4.3 Feature Group C: Agentic Behaviors

#### FR-3.1: Schema Introspection & Dynamic SQL Generation

**Description:** AI introspects schema at startup and generates SQL dynamically.

**Behavior:**
- At session start, `introspect_schema()` returns table/column/FK definitions
- AI generates SQL queries based on its schema understanding
- All queries use `?` placeholders (parameterized)
- DDL operations blocked at the tool level

#### FR-3.2: Proactive Insights (Not Yet Implemented)

**Description:** AI surfaces trends and anomalies. *Scheduled for Phase 3.*

#### FR-3.3: Preference Learning (Not Yet Implemented)

**Description:** Cross-session user preference learning. *Tools exist but LLM does not call them yet.*

---

## 5. A2UI Component Catalog

### 5.1 Component Payload Format

```typescript
interface A2UIPayload {
  componentType: ComponentType;
  title?: string;
  description?: string;
  data: unknown;
  config?: Record<string, unknown>;
  action?: {
    type: "create" | "update" | "delete";
    entity: string;
    recordId?: string | number;
    oldValues?: Record<string, unknown>;
    newValues: Record<string, unknown>;
  };
}

interface A2UICompositePayload {
  componentType: "dashboard";
  title?: string;
  layout: "vertical" | "grid" | "sidebar";
  components: A2UIPayload[];
}
```

### 5.2 Approved Component Types

| Component Type | Description | Tested? |
|---|---|---|
| `line_chart` | Time-series trend | ✅ E2E test + screenshot |
| `bar_chart` | Categorical comparison | ⚠️ Renderer exists |
| `pie_chart` | Distribution / proportion | ⚠️ Renderer exists |
| `metric_card` | Single KPI | ✅ E2E test + screenshot |
| `stat_card` | Multiple stats | ⚠️ Renderer exists |
| `data_table` | Sortable data grid | ✅ E2E test + screenshot |
| `progress_bar` | Goal tracking | ⚠️ Renderer exists |
| `gauge` | Dial-based metric | ⚠️ Renderer exists |
| `area_chart` | Cumulative trend | ⚠️ Renderer exists |
| `confirmation_card` | Write approval gate | ✅ E2E test + screenshot |
| `error_card` | Error display | ⚠️ Renderer exists |
| `clarification_prompt` | Clarifying question | ⚠️ Renderer exists |
| `proactive_insight` | AI observation card | ⚠️ Renderer exists |
| `dashboard` | Multi-component layout | ✅ E2E test + screenshot |

---

## 6. User Experience & Design Guardrails

### 6.1 UI Component Consistency

- All views adhere to Material UI (MUI) design language
- MUI theme defined once in frontend, applied globally

### 6.2 Error Handling

- Error cards shown with friendly explanations
- No stack traces or raw JSON in chat stream

### 6.3 User Override Policy

- Users can request visualization changes via natural language
- AI re-emits new A2UI payload with requested component type

---

## 7. Success Metrics

### 7.1 KPI Definitions

| Metric | Target Goal | Measurement |
|---|---|---|
| **Task Completion Time** | < 15 seconds | Server-side timing via observability logs |
| **UI Rendering Flaw Rate** | 0% | Frontend A2UI Renderer validates every response |
| **User Confirmation Rate** | 100% | All writes must go through confirmation card |
| **SQL Generation Accuracy** | > 95% | Backend tracks successful vs failed query execution |

---

## 8. Development Scope & Out-of-Scope

### 8.1 Delivery Timeline

- **Target:** 8-12 weeks
- **Development Phases:**
  - **Phase 1 (Weeks 1-3):** Frontend scaffold, MCP Server scaffold, schema introspection
  - **Phase 2 (Weeks 4-6):** Generic query/execute tools, dynamic SQL generation, LLM integration, A2UI rendering
  - **Phase 3 (Weeks 7-9):** Write flows with confirmation gate, composite dashboards
  - **Phase 4 (Weeks 10-12):** E2E test suite, observability, PoC demo preparation

### 8.2 In-Scope Summary

| Feature Area | Status |
|---|---|
| 3 SQLite tables: clients, sales, contacts + user_preferences | ✅ Implemented |
| Java Spring Boot backend with ToolDispatcher | ✅ Implemented |
| 6 MCP tools: query, execute, introspect_schema, resolve_entity, track_preference, get_preferences | ✅ Implemented |
| React + TypeScript + Vite + MUI frontend | ✅ Implemented |
| A2UI component catalog (14 components) | ✅ Implemented (5 tested) |
| Schema introspection & dynamic SQL generation | ✅ Implemented |
| Write confirmation gate with token expiry | ✅ Implemented |
| Composite dashboard rendering | ✅ Implemented |
| Backend LLM orchestration (LlmChatService) | ✅ Implemented |
| Observability logs across all components | ✅ Implemented |
| E2E test suite (13 tests, 100% pass) | ✅ Implemented |
| Screenshot evidence for all tests | ✅ Implemented |

### 8.3 Future Phase (Not Yet Implemented)

| Feature Area | Status |
|---|---|
| Self-healing query recovery | ❌ Future |
| Proactive insights & anomaly detection | ❌ Future |
| Dynamic derived metrics | ❌ Future |
| Cross-session preference learning | ❌ Future |
| Analytics pipeline (PostHog) | ❌ Future |
| Multi-provider LLM UI configuration | ❌ Future |

---

## Appendix A: Terminology Glossary

| Term | Definition |
|---|---|
| **A2UI** | Agent-to-User Interface — declarative UI protocol where the AI outputs structured JSON |
| **MCP** | Model Context Protocol — standard for connecting AI agents to tools |
| **MUI** | Material UI — React component library |
| **LLM** | Large Language Model |
| **PoC** | Proof of Concept |
| **ToolDispatcher** | Java service for in-process tool dispatch |
| **LlmChatService** | Java service orchestrating the LLM function-calling loop |
| **Schema Introspection** | AI dynamically queries DB schema at startup |
| **Human-in-the-Loop** | Explicit human approval before data modifications |

---

## Appendix B: Decision Log

| # | Decision | Rationale |
|---|---|---|
| D01 | 3 database entities: clients, sales, contacts | Sufficient richness for PoC demos |
| D02 | SQLite database | Zero-setup, ships easily |
| D03 | Java Spring Boot backend | Enterprise-aligned, team skill fit |
| D04 | Backend LLM orchestration via RestClient | Eliminates fragile frontend function-calling loop |
| D05 | A2UI declarative protocol | Maximum AI agency, clean separation of concerns |
| D06 | Full transparency confirmation card | Human-in-the-Loop Safety |
| D07 | Material UI design system | Mature ecosystem, strong React integration |
| D08 | React + TypeScript + Vite frontend | MUI's natural habitat, fast dev iteration |
| D09 | Stdio MCP transport (secondary) | Available for desktop LLM hosts (Claude Desktop) |
| D10 | Observability-first logging | End-to-end traceability for debugging |
