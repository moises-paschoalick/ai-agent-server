# Agent Server (ai-agent-server)

[![CI](https://github.com/moises-paschoalick/ai-agent-server/actions/workflows/ci.yml/badge.svg)](https://github.com/moises-paschoalick/ai-agent-server/actions/workflows/ci.yml)

Agent server in Java/Spring with LangChain4j, plus a web playground. The goal is an API compatible with the OpenAI Assistants API (`/v1`), with tools executed both on the server and by the client, routed to multiple LLM providers.

This is a monorepo:

```
backend/     Agent Server (Java 21, Spring Boot, LangChain4j, Gradle)
frontend/    Playground (React, Vite, Tailwind): chat, client tool cycle, threads, tools, model, prompt, /v1 assistants (CRUD)
docker-compose.yml      backend + frontend
Dockerfile              single image: backend + frontend in one container (see DEPLOYMENT.md)
.env.example            one .env at the root for everything
```

### Quick start

```bash
cp .env.example .env    # set OPENAI_API_KEY, AGENT_API_KEYS and AGENT_API_KEY (one of AGENT_API_KEYS)
docker compose up --build
```

Open http://localhost:3000 for the playground; the API is at http://localhost:8080.

> **Status:** feature complete for the planned scope. The OpenAI-compatible `/v1` API (assistants, threads, messages, runs with polling) and the sync API (`/chat` with client tools) are implemented, checked field by field against OpenAI's object format. The playground talks to the sync API and manages the `/v1` assistants. Backend and frontend ship as containers for Railway or any Docker host. See [Known differences](#known-differences-from-openai) before migrating.

---

## 🚀 Tech Stack

| Layer | Choice | Version |
| --- | --- | --- |
| Language | Java (Gradle toolchain) | 21 |
| Framework | Spring Boot (webmvc) | 4.1.2 |
| LLM | LangChain4j (via BOM): `open-ai`, `anthropic`, `google-ai-gemini` | 1.21.0 |
| Concurrency | Virtual threads | Java 21 |
| Build | Gradle wrapper | — |
| Frontend | React, Vite, TypeScript, Tailwind CSS (nginx in the container) | 19, 6, 5.8, 4 |

---

## 📋 Prerequisites

- Java 21+
- An API key for at least one LLM provider (OpenAI by default)
- Node.js 22 (frontend)
- Docker with Compose v2 (optional: container run)

No local Gradle install is needed; use `./gradlew` inside `backend/`.

> **Moving from the single-project layout:** the Java project now lives in `backend/`. In IntelliJ, open the repository root and link `backend/` as the Gradle project (or open `backend/` directly). Run the server from `backend/`; its default data directory becomes `backend/data/` (move your old `data/` there or set `AGENT_DATA_DIR`).

---

## ⚙️ Configuration

Copy the template and fill in at least one key. One `.env` at the repository root serves everything: the backend imports it (from `backend/` it reads `../.env`, then a local `.env` if there is one), the frontend's dev server reads it, and `docker compose` uses it. It is git-ignored.

```bash
cp .env.example .env
```

```properties
OPENAI_API_KEY=sk-...
# key that clients of this server must send (see Authentication)
AGENT_API_KEYS=<output of: openssl rand -hex 24>
# the key the frontend's proxy sends (one of AGENT_API_KEYS)
AGENT_API_KEY=<same value>
# optional, enable more providers:
OPENROUTER_API_KEY=sk-or-...
```

[`.env.example`](.env.example) lists every supported variable.

### Providers

A provider is active when its key is set; without a key it stays declared but inactive. The default provider must be active or the application does not start.

| Provider id | Type | Key variable | Base URL | Live-verified |
| --- | --- | --- | --- | --- |
| `openai` | `openai-compatible` | `OPENAI_API_KEY` (or `LLM_API_KEY`) | `AGENT_LLM_BASE_URL`, default `https://api.openai.com/v1` | yes |
| `openrouter` | `openai-compatible` | `OPENROUTER_API_KEY` | `https://openrouter.ai/api/v1` | not yet |
| `xai` (Grok) | `openai-compatible` | `XAI_API_KEY` | `https://api.x.ai/v1` | not yet |
| `anthropic` | `anthropic` | `ANTHROPIC_API_KEY` | provider default | not yet |
| `gemini` | `gemini` | `GEMINI_API_KEY` | provider default | not yet |

Any other OpenAI-compatible API needs only configuration, e.g.:

```properties
agent.llm.providers.groq.type=openai-compatible
agent.llm.providers.groq.base-url=https://api.groq.com/openai/v1
agent.llm.providers.groq.api-key=${GROQ_API_KEY:}
```

### Choosing a model

Wherever a `model` is accepted, it is resolved in this order:

1. empty → `agent.llm.default`
2. an alias name → the alias target
3. `provider:model` when the prefix is a declared provider (split at the first `:`, so `openrouter:meta-llama/llama-3.3-70b-instruct:free` works)
4. anything else → the default provider with the whole value as model name

Aliases:

```properties
agent.llm.aliases.fast.model=openrouter:meta-llama/llama-3.3-70b-instruct:free
agent.llm.aliases.fast.temperature=0.2
```

### General settings

| Variable | Default | Purpose |
| --- | --- | --- |
| `AGENT_LLM_DEFAULT` | `openai:${AGENT_LLM_MODEL:gpt-5.4-mini}` | default model (`provider:model`) |
| `AGENT_LLM_TEMPERATURE` | `0.3` | default temperature (call > alias > this) |
| `AGENT_LLM_TIMEOUT` | `60s` | timeout per LLM call (overridable per provider) |
| `AGENT_LLM_MAX_RETRIES` | `2` | client retries (overridable per provider) |
| `AGENT_STORE` | `memory` | where data is kept: `memory` (conversations and runs in memory, assistants and tools in `AGENT_DATA_DIR`) or `postgres` (everything in PostgreSQL, see [Storage](#storage)) |
| `DATABASE_URL` | (empty) | PostgreSQL for `AGENT_STORE=postgres`: `postgresql://user:password@host:port/db` (as Railway gives it) or `jdbc:postgresql://...`; the password is never logged |
| `AGENT_AUDIT_RETENTION` | `90d` | audit events older than this are deleted once a day |
| `AGENT_DATA_DIR` | `./data` | directory of `tools.json` and `assistants.json` (git-ignored); with `postgres`, only read for the first import |
| `AGENT_TIMEZONE` | `America/Sao_Paulo` | time zone of the date and time system tools |
| `AGENT_SYSTEM_TOOLS_MODE` | `all` | system tools in executions with an assistant: `all` = every one; `declared` = only those the assistant declares (see [System tools](#system-tools)) |
| `AGENT_ASSISTANTS_PERSIST` | `true` | `/v1` assistants saved in `data/assistants.json`; `false` keeps them in memory only (use for real/sensitive data) |
| `AGENT_MAX_TOOL_ROUNDS` | `10` | server-side tool rounds per user message before stopping |
| `PORT` | `8080` | HTTP port (platforms such as Railway set it) |
| `AGENT_<PROVIDER>_MODELS` | (empty) | models of that provider listed by `GET /models`, comma-separated (`AGENT_OPENAI_MODELS`, `AGENT_ANTHROPIC_MODELS`, `AGENT_GEMINI_MODELS`, `AGENT_OPENROUTER_MODELS`, `AGENT_XAI_MODELS`) |
| `AGENT_RUN_EXPIRES` | `600s` | how long a `/v1` run may wait in `requires_action` before it becomes `expired` |
| `AGENT_CHAT_SYSTEM_PROMPT` | (empty) | initial system prompt of `/chat` (changeable with `PUT /system-prompt`) |

`LLM_API_KEY`, `AGENT_LLM_MODEL` and `AGENT_LLM_BASE_URL` from older setups still work. Environment variables work as well as `.env`. Never commit keys.

---

### Storage

With the default `AGENT_STORE=memory`, conversations and runs live in memory (a restart loses them) and assistants and tools are saved in `AGENT_DATA_DIR`. With `AGENT_STORE=postgres` and `DATABASE_URL`, **everything** goes to PostgreSQL: assistants, tools, threads, messages, runs and the audit trail. A redeploy then keeps every conversation, and no volume is needed on the backend.

- **Tables** are created by Flyway migrations at startup (`backend/src/main/resources/db/migration`).
- **First start on an empty database:** `assistants.json` and `tools.json` from `AGENT_DATA_DIR` are imported with the same ids, so the `assistant_id` of existing flows keeps working. Without `tools.json`, the catalog starts with the seed. A table that already has rows is never imported into.
- **After a restart:** runs that were `queued` or `in_progress` become `failed` with `server_error` ("The server restarted while the run was in progress."); `cancelling` becomes `cancelled`; `requires_action` stays, and `submit_tool_outputs` resumes it.
- **Database down:** the server does not start (the message shows `DATABASE_URL` without the password); while running, routes that need it answer 503 `service_unavailable`, and `GET /health` stays up.
- **pgvector:** the compose image (`pgvector/pgvector:pg17`) has the `vector` extension for RAG later; the startup log says whether it is available. Nothing uses it yet.

```bash
# .env: AGENT_STORE=postgres and DATABASE_URL=postgresql://agent:agent@postgres:5432/agent
docker compose --profile postgres up --build
```

#### Audit trail

Every model call, tool call, run status change and change to assistants, tools and threads leaves an event, with `latency_ms` and tokens where they apply. Events never hold message text, tool arguments or results, instructions or keys: the caller is a 12-character hash of its API key. They outlive the thread they refer to and are deleted after `AGENT_AUDIT_RETENTION`. With `memory`, the last 1,000 events are kept in memory. This is where to look first when an answer is slow:

```bash
curl -s -H "Authorization: Bearer $KEY" "http://localhost:8080/audit/events?thread_id=$ID&type=llm_call"
# [{"id":12,"at":"2026-11-03T14:02:11Z","type":"llm_call","thread_id":"thread_...","model":"openai:gpt-5.4-mini",
#   "latency_ms":2140,"prompt_tokens":812,"completion_tokens":64,"total_tokens":876,"outcome":"tool_call",...}]
```

### Authentication

Every route requires `Authorization: Bearer <key>`, with a key listed in `AGENT_API_KEYS` (comma-separated; independent from the LLM provider keys). Only `GET /health` and CORS preflight (`OPTIONS`) are open.

```bash
openssl rand -hex 24            # generate a key, put it in AGENT_API_KEYS in .env
export KEY=<that key>           # used by the curl examples below
curl -s http://localhost:8080/health                                   # open: {"status":"UP"}
curl -s -H "Authorization: Bearer $KEY" http://localhost:8080/tools    # protected
```

Without a valid key the answer is 401 `{"error":{"message":"...","code":"invalid_api_key"}}` (under `/v1/` also `type` and `param`); a wrong key is shown masked (`sk-lo***xyz`). With `AGENT_API_KEYS` empty the server still starts, logs a warning and answers 401 on every protected route. The API is stateless: no session, cookie or CSRF token.

## ▶️ Running

```bash
cd backend && ./gradlew bootRun     # API on http://localhost:8080 (or PORT)
cd frontend && npm install && npm run dev   # playground on http://localhost:3000
```

The frontend only calls `/api` on its own origin. Its dev server proxies `/api/*` to `BACKEND_URL` (default `http://localhost:8080`) and adds `Authorization: Bearer $AGENT_API_KEY` there, so the key never reaches the browser and the backend needs no CORS. See [`frontend/README.md`](frontend/README.md).

At the top of the sidebar, the **Assistant** dropdown lists every `/v1` assistant, newest first, with buttons to create (+), edit (✎) and delete (🗑, asks for confirmation). Create and edit open an editor with every field: name, description, model, instructions, temperature, top_p, tools (JSON list in the OpenAI format, as in an export) and metadata; when editing, also the copyable id and the JSON as saved. It goes through the same `/v1` routes as any client. With an assistant picked, the model, system instructions and tools of the sidebar are that assistant's and save to it; "Sem assistant" shows the global settings. Every message goes with the picked `assistant_id`. The thread menu in the chat header lists every thread (`GET /threads/summaries`) grouped by the last assistant that answered, Node-RED threads included, with search; opening a thread picks that assistant, and picking an assistant opens its most recent thread. Debug mode (`</>`) is at the right of the chat header. Anyone who opens the frontend can read, change and delete every assistant, so protect it before exposing it (see [Railway](#railway)).

### Docker

**Single image** (one container, lowest hosting cost): the root `Dockerfile` puts the backend and the playground behind one nginx on one port, with data in `/data` or in PostgreSQL. See [`DEPLOYMENT.md`](DEPLOYMENT.md), which also covers using your own PostgreSQL server and scaling.

```bash
docker build -t agent-server .
docker run -p 8080:8080 -e OPENAI_API_KEY=... -e AGENT_API_KEYS=... -e FRONTEND_USER=admin -e FRONTEND_PASSWORD=... -v agent-server-data:/data agent-server
```

**Separate images**, with Compose:

```bash
docker compose up --build        # reads .env; frontend on :3000, backend on :8080
```

The backend image (`backend/Dockerfile`) builds the jar and runs it on a Java 21 JRE as a non-root user, with a healthcheck on `/health`. It reads its settings from environment variables only; `.env`, `data/` and `build/` never go into the image. Tools and assistants are kept in the `agent-server-data` volume (`/app/data`), so they survive restarts; `docker compose down -v` removes them. With `--profile postgres`, a `postgres` service (pgvector image, `agent-server-pg` volume, no published port) starts too; set `AGENT_STORE=postgres` and `DATABASE_URL` to use it (see [Storage](#storage)). `AGENT_PORT` and `FRONTEND_PORT` change the host ports.

The frontend image (`frontend/Dockerfile`) builds the playground and serves it with nginx, which proxies `/api/` to `BACKEND_URL` with `AGENT_API_KEY`. With `FRONTEND_USER` and `FRONTEND_PASSWORD` set, nginx asks for basic auth on every page and on `/api`.

Without Compose:

```bash
docker build -t agent-server backend
docker run -p 8080:8080 -e OPENAI_API_KEY=... -e AGENT_API_KEYS=... -v agent-server-data:/app/data agent-server
```

### Railway

**One service** (single image): root directory `/`, a volume at `/data` and `RAILWAY_RUN_UID=0` (Railway mounts volumes as root), or PostgreSQL instead of the volume. See [`DEPLOYMENT.md`](DEPLOYMENT.md#railway).

Or **two services** from this repository:

1. **Backend**: root directory `backend`. Railway builds `backend/Dockerfile` and injects `PORT`, which the server uses. Set the variables from [`.env.example`](.env.example): at least `AGENT_API_KEYS` and the key of the default provider. For data that survives redeploys, add a PostgreSQL service (the template with pgvector, so RAG can use it later) and set `AGENT_STORE=postgres` and `DATABASE_URL=${{Postgres.DATABASE_URL}}` on the backend: everything, conversations included, goes there, and no volume is needed. Without it (`AGENT_STORE=memory`), add a volume at `/app/data`, or every redeploy erases tools and assistants; conversations are lost on every restart either way. Optional: the health check path `/health`.
2. **Frontend**: root directory `frontend`. Set `BACKEND_URL` to the backend's private address (e.g. `http://<backend-service>.railway.internal:<port>`), `AGENT_API_KEY` to one of the backend keys and, before giving it a public domain, `FRONTEND_USER` and `FRONTEND_PASSWORD`: anyone who can open the frontend can use the API through its proxy and read, change or delete every assistant.

Run a **single instance**, with either store: the lock per thread, the run worker and the cancel flags are local to the process (see [Scaling](DEPLOYMENT.md#scaling)). With `memory`, a restart also loses threads and runs (clients get 404 and start a new thread). Keep the database private: it holds conversations and prompts; the audit trail holds neither text nor keys.

## 🔌 Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/health` | open liveness check → `{"status":"UP"}` (no key needed) |
| `POST` | `/threads` | create a conversation → `{"conversation_id":"thread_..."}` |
| `GET` | `/threads` | list conversation ids |
| `GET` | `/threads/summaries` | every conversation, latest activity first: `conversation_id`, `created_at`, `assistant_id` (the last assistant that answered), `title` (first user message, 80 chars), `message_count`, `last_activity_at` |
| `GET` | `/threads/{id}/history` | messages of a conversation (404 if unknown) |
| `DELETE` | `/threads/{id}` | delete → `{"message":"Thread deleted"}` (404 if unknown) |
| `POST` | `/chat` | `{"conversation_id","prompt","assistant_id"?}` → `message` or `tool_call` (see below); 404 if the conversation or the assistant does not exist; 429/502/504 on LLM errors |
| `POST` | `/tools/result` | `{"conversation_id","tool_name","tool_output","tool_call_id"?}` → next `tool_call` or the resumed answer |
| `GET` / `PUT` | `/system-prompt` | `{"system_prompt"}` used by `/chat` without `assistant_id` (in memory; `""` = none) |
| `GET` / `PUT` | `/model` | `{"model"}` used by `/chat` without `assistant_id` (in memory; `""` = `AGENT_LLM_DEFAULT`) |
| `GET` | `/models` | models to pick from: the default, the aliases, then `AGENT_<PROVIDER>_MODELS` of every active provider (e.g. `["openai:gpt-5.4-mini","fast","openai:gpt-5.4"]`) |
| `GET` | `/tools` | every tool the model can call, each with `kind` `dynamic` or `system` |
| `GET` | `/tools/{name}` | one tool (404 if unknown) |
| `POST` | `/tools` | create a dynamic tool → 201 |
| `PUT` | `/tools/{name}` | replace a dynamic tool (name cannot change) |
| `DELETE` | `/tools/{name}` | delete a dynamic tool → `{"message":"Tool deleted"}` |
| `POST` | `/tools/generate` | `{"description","model"?}` → a tool suggested by the model, `{"name","description","parameters"}`; nothing is saved |
| `GET` | `/audit/events` | audit trail, newest first; filters `thread_id`, `run_id`, `assistant_id`, `type`, `since` (ISO-8601) and `limit` (1–500, default 50); see [Audit trail](#audit-trail) |

```bash
ID=$(curl -s -H "Authorization: Bearer $KEY" -X POST http://localhost:8080/threads | sed 's/.*"conversation_id":"\([^"]*\)".*/\1/')
curl -s -H "Authorization: Bearer $KEY" -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d "{\"conversation_id\":\"$ID\",\"prompt\":\"hello\"}"
curl -s -H "Authorization: Bearer $KEY" http://localhost:8080/threads/$ID/history
```

Responses of `/chat` and `/tools/result`:

```json
{"type":"message","content":"...","metadata":{"latency_ms":812,"ttft_ms":812,"total_tokens":143,"model":"gpt-5.4-mini-..."}}
{"type":"tool_call","tool":"get_order_status","tool_input":{"order_id":"A-1001"},"tool_call_id":"call_..."}
```

`ttft_ms` is the time to the first piece of text; without streaming it equals `latency_ms`.

#### Streaming

With `"stream": true` in the body of `/chat` or `/tools/result`, the answer is `text/event-stream` (server-sent events): a `delta` for each piece of text as the model writes it, then exactly one `message`, `tool_call` or `error`. `message` and `tool_call` are the same objects as above, and the `delta` pieces add up to the final `content`. When the model calls a system tool in the middle, the text of the next call keeps coming in the same stream.

```bash
curl -N -H "Authorization: Bearer $KEY" -X POST http://localhost:8080/chat -H "Content-Type: application/json" \
  -d "{\"conversation_id\":\"$ID\",\"prompt\":\"tell me a short story\",\"stream\":true}"
# event: delta
# data: {"content":"Once "}
#
# event: delta
# data: {"content":"upon a time"}
# ...
# event: message
# data: {"type":"message","content":"Once upon a time...","metadata":{"latency_ms":1830,"ttft_ms":410,...}}
```

- **Errors before the first event** are the usual JSON with its HTTP status: invalid body (400), unknown thread or assistant (404), active run (400), and also a model error before any text (429, 502, 504). Nothing is sent until the first event.
- **Errors after it** are an `error` event, `{"code","message"}` with the codes above (`timeout`, `rate_limit_exceeded`, `server_error`), and the stream ends.
- Only the complete answer is saved. A client that disconnects stops the generation, frees the thread for the next message, and leaves no answer saved.
- Behind a proxy, turn off response buffering for these routes; the server sends `X-Accel-Buffering: no` and `Cache-Control: no-cache`, and the frontend's nginx is already set up.
- `/v1` runs do not stream (see below).

Client tool cycle:

```bash
curl -s -H "Authorization: Bearer $KEY" -X POST http://localhost:8080/chat -H "Content-Type: application/json" \
  -d "{\"conversation_id\":\"$ID\",\"prompt\":\"What is the status of order A-1001?\"}"
# -> {"type":"tool_call","tool":"get_order_status","tool_input":{"order_id":"A-1001"},"tool_call_id":"call_..."}
curl -s -H "Authorization: Bearer $KEY" -X POST http://localhost:8080/tools/result -H "Content-Type: application/json" \
  -d "{\"conversation_id\":\"$ID\",\"tool_name\":\"get_order_status\",\"tool_output\":\"shipped\"}"
# -> {"type":"message","content":"Your order A-1001 has been shipped.", ...}
```

### How `/chat` works

1. A pending client tool call from the previous turn is closed with `Tool call not executed.`
2. The user message is stored and the history + every catalog tool go to the default model.
3. System tools (e.g. `get_current_datetime`, `calculate`) run on the server and the model is called again, up to `AGENT_MAX_TOOL_ROUNDS` rounds.
4. A text answer comes back as `message`. A client tool comes back as `tool_call`; send its output to `/tools/result` (`tool_output` may be `""`; without `tool_call_id` the oldest pending call is used).
5. Several client tools in one turn are handed out one per response; the model is called again only when all have results.

Rules of `/tools/result`: missing `tool_output` or `tool_name` → 400; `tool_name` different from the pending call → 400; no pending call or `tool_call_id` not pending → 409. Tool rounds count from the last user message, including resumed turns.

`/threads/{id}/history` lists only user messages and text answers; tool calls stay internal.

As in OpenAI, a thread belongs to no assistant: it only keeps the conversation. A `/chat` with `assistant_id` works like a `/v1` run: that message uses the assistant's model, instructions, tools, `temperature` and `top_p`, read on every call, instead of the global prompt, model and `/tools` catalog, and the answer is recorded with that `assistant_id`. `/tools/result` goes on with the assistant of the turn that asked for the tool (404 if it was deleted meanwhile). The assistant's function tools are client tools, as in a run. Different assistants can answer in the same thread; the model always gets the whole history.

### Tools

Dynamic tools are executed by the client; system tools (see below) run on the server and cannot be changed or removed (409). `parameters` is a JSON Schema object and is stored exactly as sent; `enum`, nested objects, arrays and nullable types (`["string","null"]`) are supported.

```bash
curl -s -H "Authorization: Bearer $KEY" -X POST http://localhost:8080/tools -H "Content-Type: application/json" -d '{
  "name": "get_invoice",
  "description": "Get a fictitious invoice",
  "parameters": {
    "type": "object",
    "properties": {
      "invoice_id": {"type": "string"},
      "currency": {"type": ["string", "null"], "enum": ["BRL", "USD", null]}
    },
    "required": ["invoice_id"]
  }
}'
curl -s -H "Authorization: Bearer $KEY" http://localhost:8080/tools/get_invoice
curl -s -H "Authorization: Bearer $KEY" -X DELETE http://localhost:8080/tools/get_invoice
```

Names: 1–64 letters, digits, `_` or `-`, unique across dynamic and system tools (409 otherwise).

The catalog is saved in `data/tools.json` (atomic writes). On the first start it is created from a fictitious seed (`get_weather`, `get_order_status`, `list_orders`). To start over, stop the app and delete `data/tools.json`. An unreadable file is kept as `tools.json.invalid-<epoch>` on the next write.

#### Generating a tool with the model

`POST /tools/generate` turns a plain description into a tool definition, using `model` or, when empty, the global model of `/model`. Nothing is saved: the client reviews the result and creates it with `POST /tools` or puts it in an assistant.

```bash
curl -s -H "Authorization: Bearer $KEY" -X POST http://localhost:8080/tools/generate -H "Content-Type: application/json" \
  -d '{"description":"looks up the status of an order by its number"}'
# {"name":"get_order_status","description":"...","parameters":{"type":"object","properties":{"order_id":{...}},"required":["order_id"]}}
```

The reply is checked with the catalog rules (name, not a system tool name, `parameters` schema with an `object` root). An invalid reply gets one more attempt with the reason; a second invalid reply answers 502 `server_error` with that reason. A blank `description`, one over 2,000 characters, or a model of an inactive provider answers 400 without calling the model. Each generation costs one or two model calls.

#### System tools

They run on the server, are deterministic and never reach other systems (anything that calls an external API stays a client tool, e.g. in a Node-RED flow). A failure goes back to the model as `Error: <reason>`, so it can correct the call.

| Tool | Arguments | Example → result |
| --- | --- | --- |
| `get_current_datetime` | none | `2026-10-27T15:00:00-03:00` (in `AGENT_TIMEZONE`) |
| `calculate` | `expression`: `+ - * / ^`, parentheses, `round(x[, n])`, `floor`, `ceil`, `abs`, `sqrt`, `min`, `max` | `round((1250*0.15)+32, 2)` → `219.5`; exact decimals (`0.1+0.2` → `0.3`), no `eval` |
| `date_calc` | `operation` `add` (`date`, `amount`, `unit`) or `diff` (`start`, `end`, `unit`); `unit` days, weeks, months, years (add only), business_days; `holidays` `BR` (default) or `none` | `add` 5 `business_days` from `2026-12-18` → `{"result":"2026-12-28","weekday":"monday"}` (skips Christmas) |
| `convert_timezone` | `to` (IANA), `datetime` (empty = now), `from` (default `AGENT_TIMEZONE`, ignored when `datetime` has an offset) | `2026-10-27T15:00:00` from `America/Sao_Paulo` to `Europe/Lisbon` → `2026-10-27T18:00:00Z` |

Business days skip weekends and the national holidays of Brazil (1/1, Good Friday, 21/4, 1/5, 7/9, 12/10, 2/11, 15/11, 20/11, 25/12); Carnival and Corpus Christi are optional days off and count as business days.

An assistant declares a system tool as a function tool with its name, e.g. `{"type":"function","function":{"name":"calculate"}}` (description and parameters are optional and ignored: the server's definition is sent to the model, and the call never becomes `requires_action`). With `AGENT_SYSTEM_TOOLS_MODE=all` (default) every execution gets every system tool, as before. With `declared`, a run or a `/chat` with `assistant_id` gets only the declared ones; `/chat` without an assistant always gets all. To switch an existing setup to `declared`, first declare the tools each assistant needs (the frontend editor has a checkbox per system tool), then change the variable, or those assistants lose `get_current_datetime`.

## 🤖 OpenAI-compatible API (`/v1`)

Clients of the OpenAI Assistants API can point to this server by changing only the base URL, the key and the `assistant_id`. Assistants, threads, messages and runs (with polling) are implemented; streaming and run steps are not. Extra headers such as `OpenAI-Beta: assistants=v2` are accepted and ignored.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/v1/assistants` | create; accepts an OpenAI export as is (new `asst_` id; `code_interpreter`/`file_search` → 400, `retrieval` ignored) |
| `GET` | `/v1/assistants` | list (`limit`, `order`, `after`, `before`) |
| `GET` / `POST` / `DELETE` | `/v1/assistants/{id}` | read, partial update, delete (`assistant.deleted`) |
| `POST` | `/v1/threads` | create, optionally with `messages` and `metadata` |
| `GET` / `POST` / `DELETE` | `/v1/threads/{id}` | read, update `metadata`, delete (`thread.deleted`) |
| `POST` | `/v1/threads/{id}/messages` | `{"role":"user"|"assistant","content":"..." or text parts,"metadata"?}` |
| `GET` | `/v1/threads/{id}/messages` | list visible messages, newest first (`limit`, `order`, `after`, `before`, `run_id`) |
| `GET` | `/v1/threads/{id}/messages/{msg_id}` | one message |
| `POST` | `/v1/threads/{id}/runs` | start a run: `assistant_id` plus optional `model`, `instructions`, `additional_instructions`, `additional_messages`, `tools`, `temperature`, `top_p`, `metadata` (`stream:true` → 400) |
| `POST` | `/v1/threads/runs` | create a thread (`thread.messages`, `thread.metadata`) and start a run on it |
| `GET` | `/v1/threads/{id}/runs` | list runs, newest first (`limit`, `order`, `after`, `before`) |
| `GET` | `/v1/threads/{id}/runs/{run_id}` | one run (poll this) |
| `POST` | `/v1/threads/{id}/runs/{run_id}/submit_tool_outputs` | `{"tool_outputs":[{"tool_call_id","output"}]}` for every pending call |
| `POST` | `/v1/threads/{id}/runs/{run_id}/cancel` | cancel an active run |

### Migrating a client from OpenAI

A client written for the OpenAI Assistants API (an SDK, Node-RED, Postman...) moves over in four steps:

1. **Register the assistant**: send its OpenAI export, or the same JSON used to create it, to `POST /v1/assistants`. The server creates it with a new `asst_` id; set `model` to a model of this server (e.g. `openai:gpt-5.4-mini`).
2. **Change the base URL**: `https://api.openai.com/v1` → `https://<your-host>/v1`.
3. **Change the key variable**: send a key from `AGENT_API_KEYS` instead of the OpenAI key (`Authorization: Bearer ...`).
4. **Change the `assistant_id`**: use the id returned in step 1.

Nothing else changes: the same calls, polling and `submit_tool_outputs`.

### Known differences from OpenAI

- No streaming on `/v1` runs (`stream: true` → 400) and no run steps; the sync `/chat` streams (see [Streaming](#streaming)).
- Only `function` tools; `code_interpreter` and `file_search` → 400 (`retrieval` is ignored). `tool_resources` is always `{}`.
- `tool_choice`, `max_prompt_tokens`, `max_completion_tokens`, `truncation_strategy` and `response_format` on runs are accepted but have no effect (returned as `auto`/`null`).
- Messages are text only; `attachments` and `annotations` are always empty.
- Single instance. With `AGENT_STORE=memory` (default), threads and runs are lost on restart and assistants and tools are saved to `data/`; with `postgres`, everything survives (see [Storage](#storage)).
- Errors follow the OpenAI format, but messages and `code` values are this server's own.

Threads are the same conversations as `/threads` (a thread created on either side works on both). Assistant tools of type `function` follow the same name and schema rules as `/tools`.

```bash
# register an assistant from an OpenAI export (fictitious example)
curl -s -H "Authorization: Bearer $KEY" -X POST http://localhost:8080/v1/assistants \
  -H "Content-Type: application/json" -d '{
  "id": "asst_from_openai", "object": "assistant", "created_at": 1700000000,
  "name": "Orders support", "model": "openai:gpt-5.4-mini",
  "instructions": "Help customers with their orders.",
  "tools": [{"type": "function", "function": {"name": "get_order_status",
    "parameters": {"type": "object", "properties": {"order_id": {"type": "string"}}, "required": ["order_id"]}}}]
}'
# thread with a first message, then the latest messages
TID=$(curl -s -H "Authorization: Bearer $KEY" -X POST http://localhost:8080/v1/threads \
  -H "Content-Type: application/json" -d '{"messages":[{"role":"user","content":"Hi!"}]}' \
  | sed 's/.*"id":"\([^"]*\)".*/\1/')
curl -s -H "Authorization: Bearer $KEY" "http://localhost:8080/v1/threads/$TID/messages?limit=2"
```

### Runs

A run executes an assistant on a thread in the background. `POST /runs` answers at once with `status: "queued"`; the client polls `GET /runs/{run_id}` until the status changes:

| Status | Meaning | Client does |
| --- | --- | --- |
| `queued`, `in_progress` | waiting or calling the model | keep polling |
| `requires_action` | the model asked for client tools; `required_action.submit_tool_outputs.tool_calls` lists all of them (`function.arguments` is a JSON string) | run the tools, `POST .../submit_tool_outputs`, keep polling |
| `cancelling` | cancel requested while the model was answering; the answer is discarded | keep polling |
| `completed` | done; the answer is the newest message of the thread (with `run_id` and `assistant_id`) | `GET .../messages?limit=1` |
| `incomplete` | server-side tool rounds hit `AGENT_MAX_TOOL_ROUNDS` (`incomplete_details.reason`) | |
| `failed` | the model call failed (`last_error.code`, e.g. `rate_limit_exceeded`) | |
| `cancelled`, `expired` | cancelled, or left in `requires_action` longer than `AGENT_RUN_EXPIRES` (default `600s`); pending calls get `Tool call cancelled.` / `Tool call expired.` | |

The run uses its own `model`, `instructions`, `tools`, `temperature` and `top_p` when given, otherwise the assistant's; `additional_instructions` are appended after a blank line. Only the run's (or assistant's) tools and the system tools reach the model, never the global `/tools` catalog. `usage` sums every model call of the run and is filled once the run ends. `tool_choice` is accepted and returned but not applied.

A thread has at most one active run: creating another run, adding a message (`/v1` or `/chat`, `/tools/result`) while one is active answers 400 until the run ends or is cancelled. With `AGENT_STORE=memory`, runs are lost on restart (the client gets 404); with `postgres`, they survive it (see [Storage](#storage)).

```bash
AID=asst_...   # from POST /v1/assistants above
H=(-H "Authorization: Bearer $KEY" -H "Content-Type: application/json")
TID=$(curl -s "${H[@]}" -X POST http://localhost:8080/v1/threads \
  -d '{"messages":[{"role":"user","content":"What is the status of order A-1001?"}]}' | jq -r .id)
RID=$(curl -s "${H[@]}" -X POST http://localhost:8080/v1/threads/$TID/runs \
  -d "{\"assistant_id\":\"$AID\"}" | jq -r .id)
# poll until requires_action
curl -s "${H[@]}" http://localhost:8080/v1/threads/$TID/runs/$RID | jq '.status, .required_action'
# -> "requires_action", {"type":"submit_tool_outputs","submit_tool_outputs":{"tool_calls":[{"id":"call_...",
#    "type":"function","function":{"name":"get_order_status","arguments":"{\"order_id\":\"A-1001\"}"}}]}}
CALL=$(curl -s "${H[@]}" http://localhost:8080/v1/threads/$TID/runs/$RID | jq -r '.required_action.submit_tool_outputs.tool_calls[0].id')
curl -s "${H[@]}" -X POST http://localhost:8080/v1/threads/$TID/runs/$RID/submit_tool_outputs \
  -d "{\"tool_outputs\":[{\"tool_call_id\":\"$CALL\",\"output\":\"shipped\"}]}" | jq .status
# poll until completed, then read the answer
curl -s "${H[@]}" http://localhost:8080/v1/threads/$TID/runs/$RID | jq '.status, .usage'
curl -s "${H[@]}" "http://localhost:8080/v1/threads/$TID/messages?limit=1" | jq -r '.data[0].content[0].text.value'
# cancel instead: POST /v1/threads/$TID/runs/$RID/cancel
```

### Errors

Every error is JSON, never HTML. Sync routes:

```json
{"error":{"message":"No thread found with id 'thread_x'.","code":"not_found"}}
```

Routes under `/v1/` use the OpenAI format (`type`: `invalid_request_error`, `rate_limit_error` or `server_error`; `param` names the offending field or is `null`):

```json
{"error":{"message":"model is required","type":"invalid_request_error","param":"model","code":"invalid_request"}}
```

| Status | `code` |
| --- | --- |
| 400 | `invalid_request` |
| 401 | `invalid_api_key` |
| 404 | `not_found` |
| 405 | `method_not_allowed` |
| 409 | `conflict` |
| 429 | `rate_limit_exceeded` |
| 500 | `server_error` |
| 502 | `server_error` |
| 504 | `timeout` |

---

## 📁 Project Structure

```
backend/src/main/java/ai/agent/
├── AgentServerApplication.java
├── api/v1/           /v1 controllers (assistants, threads, messages, runs) and DTOs
├── assistants/        Assistant model and rules; AssistantRepository (file implementation: data/assistants.json)
├── audit/             audit trail: AuditLog (memory), decorators of the stores, GET /audit/events, retention job
├── common/            Ids, pagination (ListQuery/Page), metadata rules, AtomicJsonFile
├── configuration/     AgentLlmProperties (agent.llm.*), Clock bean, run executor and scheduling
├── controller/        Chat, Thread controllers
├── dto/               request/response DTOs
├── errors/            ApiException hierarchy + ApiErrorAdvice (format chosen by path)
├── llm/               ModelRouter, ProviderRegistry, LlmGateway
│   └── provider/      OpenAI-compatible, Anthropic and Gemini adapters (only place with vendor classes)
├── engine/            AgentRunner (agent loop), RunOutcome, MessageMapper, LlmFailures
├── memory/            ConversationStore (in-memory implementation), StoredMessage (own history model)
├── persistence/       StoreConfiguration (AGENT_STORE), PostgreSQL stores, Flyway, file import, run recovery
├── runs/              Run state machine: RunService, RunWorker (virtual threads), ThreadLocks, RunExpirationJob
├── services/          ChatService, JokeDictionary
└── tools/             tool catalog: CRUD, schema validation/conversion, persistence, system tools
```

With `AGENT_STORE=memory`, conversations (threads) and runs are kept in memory and are lost on restart, and tools and assistants are saved under `data/`; with `postgres`, all of it is in the database.

Outside `backend/src/`: `backend/Dockerfile`, `Dockerfile` and `docker/single/` (single image: backend + frontend behind nginx), `docker-compose.yml` (backend + frontend), `.github/workflows/ci.yml`, and [`examples/`](examples/) (client examples: n8n flows, Node-RED, Java).

```
frontend/
├── App.tsx, components/   playground UI (sidebar: model, prompt, tools, threads; chat with tool calls)
├── services/api.ts        client of the sync API, always through /api on the same origin
├── services/mappers.ts    backend responses → UI types
├── vite.config.ts         dev server and its /api proxy with the API key
├── nginx.conf.template    container: static files + /api proxy (+ optional basic auth)
└── Dockerfile
```

---

## 🗺️ Roadmap

Delivered, in order: foundation fixes → multi-provider LLM (OpenAI, Anthropic, Gemini, Grok, OpenRouter) → full tool schema and CRUD → agent engine → sync contract → `/v1` security → `/v1` assistants, threads, messages → `/v1` runs → container.

The playground (frontend) joined the repository later, which also made it a monorepo.

Since then: assistants in the frontend, chat per assistant, system tools, the tool generator, PostgreSQL storage with an audit trail and streaming of `/chat` answers. Next: OpenTelemetry, streaming and run steps on `/v1` runs, and `file_search` with pgvector.

---

## 📚 References

- [LangChain4j docs](https://docs.langchain4j.dev)
- [Spring Boot docs](https://docs.spring.io/spring-boot/)

---

## ⚖️ License

[Apache License 2.0](LICENSE).

Agent Server is an independent project, not affiliated with or endorsed by OpenAI. OpenAI and Assistants API are trademarks of OpenAI.
