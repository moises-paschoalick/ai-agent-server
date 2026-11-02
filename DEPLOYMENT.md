# Deployment

Three ways to run Agent Server, from the cheapest to the most flexible. All of them run **one instance** (see [Scaling](#scaling)).

| Setup | Containers | Data | When |
| --- | --- | --- | --- |
| [Single image](#single-image) | 1 | `/data` volume | lowest cost: a small VPS, one Railway service, a home server |
| [Single image + PostgreSQL](#with-postgresql) | 1 + the database | PostgreSQL (bundled or your own server) | conversations must survive restarts, or you already run PostgreSQL |
| [Separate images](README.md#docker) (`docker-compose.yml`) | 2 (+ the database) | either | frontend and backend on different hosts or domains |

---

## Single image

The `Dockerfile` at the repository root builds the backend and the playground into one image. Inside the container:

- **nginx** listens on `PORT` (default `8080`) and is the only open port;
- the **backend** listens on `127.0.0.1:BACKEND_PORT` (default `8081`) and can't be reached from outside the container;
- if either process stops, the container stops, so Docker or the platform restarts both.

```bash
docker build -t agent-server .
docker run -d --name agent-server -p 8080:8080 \
  -e OPENAI_API_KEY=sk-... \
  -e AGENT_API_KEYS=$(openssl rand -hex 24) \
  -e FRONTEND_USER=admin -e FRONTEND_PASSWORD=$(openssl rand -hex 12) \
  -v agent-server-data:/data \
  --restart unless-stopped \
  agent-server
```

The playground is at `http://localhost:8080`, and the API is on the same address.

### Routes

| Path | Goes to | Authentication |
| --- | --- | --- |
| `/` (pages) | playground | basic auth, when `FRONTEND_USER` and `FRONTEND_PASSWORD` are set |
| `/api/*` | backend, called by the playground | basic auth; nginx adds the playground's key |
| `/v1/*` | backend (OpenAI-compatible API) | the client's `Authorization: Bearer <key>` |
| any request with `Authorization: Bearer ...` (`/chat`, `/tools`, `/audit/events`...) | backend, unchanged | the client's key |
| `/health` | backend | open |
| `/healthz` | nginx only | open |

So clients use the same URLs as on a standalone backend: `https://<host>/v1` for an OpenAI SDK, and `https://<host>/chat` for the sync API.

### Variables

Every backend variable from the [README](README.md#general-settings) works. The ones specific to this image:

| Variable | Default | Use |
| --- | --- | --- |
| `PORT` | `8080` | public port (nginx); platforms such as Railway set it |
| `BACKEND_PORT` | `8081` | internal port of the backend; change it only if it clashes with `PORT` |
| `AGENT_API_KEYS` | (empty) | keys for **external clients** (SDK, n8n, Node-RED), comma-separated |
| `AGENT_API_KEY` | generated at each start | key the playground's proxy uses; it is added to the accepted keys automatically and never leaves the container |
| `FRONTEND_USER` / `FRONTEND_PASSWORD` | (empty) | basic auth on the playground. **Set both before exposing the container**: anyone who opens the playground can read, change and delete every assistant |
| `AGENT_DATA_DIR` | `/data` | where `assistants.json` and `tools.json` are saved |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=60` | JVM options; the rest of the memory is left to nginx and the OS |

The image runs as a non-root user (uid `10001`) and ships no `.env`: settings come from environment variables only.

### What is kept in `/data`

With the default `AGENT_STORE=memory`:

- **kept** in `/data`: assistants (`assistants.json`) and tools (`tools.json`);
- **lost on every restart or redeploy**: threads, messages, runs and the audit trail (the last 1,000 events are kept in memory). Clients get 404 for an old thread and start a new one.

If conversations must survive restarts, use [PostgreSQL](#with-postgresql).

Backup: copy the volume while the container is running, e.g. `docker run --rm -v agent-server-data:/data -v "$PWD":/backup alpine tar czf /backup/agent-server-data.tgz -C /data .`.

---

## With PostgreSQL

With `AGENT_STORE=postgres` and `DATABASE_URL`, **everything** goes to PostgreSQL: assistants, tools, threads, messages, runs and the audit trail. A restart or redeploy keeps every conversation. Tables are created by Flyway at startup. See [Storage](README.md#storage) for what happens to runs that were in progress during a restart.

`DATABASE_URL` takes `postgresql://user:password@host:port/db` or `jdbc:postgresql://...`. Query parameters pass through to the JDBC driver, e.g. `?sslmode=require` for managed databases. The password is never logged.

**Moving from `/data` to PostgreSQL:** on the first start against an empty database, `assistants.json` and `tools.json` from `AGENT_DATA_DIR` are imported with the same ids, so the `assistant_id` in existing clients keeps working. Keep the `/data` volume mounted on that first start. After that, `/data` is no longer used.

### PostgreSQL next to the container

A `docker-compose.yml` on the server, for example:

```yaml
services:
  agent-server:
    image: agent-server        # built with: docker build -t agent-server .
    restart: unless-stopped
    ports: ["8080:8080"]
    environment:
      AGENT_STORE: postgres
      DATABASE_URL: postgresql://agent:${POSTGRES_PASSWORD}@postgres:5432/agent
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      AGENT_API_KEYS: ${AGENT_API_KEYS}
      FRONTEND_USER: ${FRONTEND_USER}
      FRONTEND_PASSWORD: ${FRONTEND_PASSWORD}
    volumes: [agent-server-data:/data]
    depends_on:
      postgres: {condition: service_healthy}

  postgres:
    image: pgvector/pgvector:pg17
    restart: unless-stopped
    environment:
      POSTGRES_USER: agent
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: agent
    volumes: [agent-server-pg:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U agent -d agent"]
      interval: 5s
      retries: 10

volumes:
  agent-server-data:
  agent-server-pg:
```

The database has no published port: only the container on the same network reaches it.

### Your own PostgreSQL server

Agent Server needs a database of its own. It only uses the `public` schema of that database, so it doesn't touch any other database on the server. As a superuser on your server:

```sql
CREATE USER agent WITH PASSWORD '<strong password>';
CREATE DATABASE agent OWNER agent;
```

Then start the container with:

```bash
-e AGENT_STORE=postgres \
-e DATABASE_URL='postgresql://agent:<password>@db.example.com:5432/agent?sslmode=require'
```

- **Version:** tested with PostgreSQL 17. The tables use plain SQL and `json`, so any supported version should work.
- **pgvector:** not needed today. The startup log says whether the `vector` extension is available; RAG will use it later.
- **Network:** the container must reach the server's port. Keep the database private, because it holds conversations and prompts.
- **Permissions:** the owner of the database is enough, since Flyway creates and changes the tables at startup.
- **Database down:** the server does not start; while running, routes that need it answer 503 and `GET /health` stays up.

---

## Railway

**Single image:** one service from this repository, root directory `/`, so Railway builds the root `Dockerfile`.

- Variables: the provider key, `AGENT_API_KEYS`, `FRONTEND_USER` and `FRONTEND_PASSWORD`.
- Add a **volume** mounted at `/data`. Railway mounts volumes as root, so also set `RAILWAY_RUN_UID=0`: the container then starts as root, hands `/data` to its own user and runs the backend and nginx as that user.
- Health check path: `/health`.

**With PostgreSQL:** add a PostgreSQL service and set `AGENT_STORE=postgres` and `DATABASE_URL=${{Postgres.DATABASE_URL}}` on the Agent Server service; then the volume is optional (only for the first import from `/data`).

Keep one replica (see below).

---

## Scaling

### Today: one instance

Run **exactly one instance**, with either store. The lock per thread, the run worker and the cancel flags live in the process memory. Two instances behind a load balancer would run the same thread twice, and a cancel sent to one would not reach a run on the other.

### Vertical: the way to grow now

Give the one instance more CPU and memory:

- **Memory:** the JVM takes `MaxRAMPercentage` of the container's memory (60% in the single image, 75% in `backend/Dockerfile`). Most of the time is spent waiting for the model, so 512 MB to 1 GB is enough to start.
- **Concurrency:** requests run on virtual threads, so many simultaneous conversations don't need more threads or CPU. The real limits are the provider's rate limits (429 shows up as `rate_limit_exceeded`) and its latency.
- **Signs that it's time:** latency in `/audit/events` growing while the provider's latency stays the same, or memory close to the limit.

### Separating frontend and backend

To put the playground on another host or domain, or to restart the backend without the playground, use the two images of [`docker-compose.yml`](docker-compose.yml) (`backend/Dockerfile` and `frontend/Dockerfile`). The data is the same, so the switch only needs the same `/data` volume or the same `DATABASE_URL`.

### Horizontal: not supported yet

Several instances need PostgreSQL plus three changes in the backend, which are on the roadmap:

1. the lock per thread in the database (row lock or advisory lock) instead of in memory;
2. the run queue in the database (`SELECT ... FOR UPDATE SKIP LOCKED`), so any instance picks up a queued run;
3. cancel flags and run expiration read from the database.

Until then, more replicas give wrong results, not more capacity.
