-- Agent Server storage (AGENT_STORE=postgres). Each object is kept whole in body (json, which keeps the key order of tool schemas); only what is
-- filtered or ordered has its own column. Times are epoch seconds, as in the API, except audit_events.at.

CREATE TABLE assistants (
    id         text   PRIMARY KEY,
    seq        bigint GENERATED ALWAYS AS IDENTITY,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    body       json   NOT NULL
);
CREATE INDEX assistants_seq ON assistants (seq);

CREATE TABLE tools (
    name       text   PRIMARY KEY,
    seq        bigint GENERATED ALWAYS AS IDENTITY,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    body       json   NOT NULL
);
CREATE INDEX tools_seq ON tools (seq);

CREATE TABLE threads (
    id         text   PRIMARY KEY,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    metadata   jsonb  NOT NULL DEFAULT '{}'
);
CREATE INDEX threads_updated ON threads (updated_at DESC);

CREATE TABLE messages (
    seq          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id           text   NOT NULL UNIQUE,
    thread_id    text   NOT NULL REFERENCES threads (id) ON DELETE CASCADE,
    role         text   NOT NULL,
    run_id       text,
    assistant_id text,
    created_at   bigint NOT NULL,
    body         json   NOT NULL
);
CREATE INDEX messages_thread ON messages (thread_id, seq);

CREATE TABLE runs (
    id         text   PRIMARY KEY,
    seq        bigint GENERATED ALWAYS AS IDENTITY,
    thread_id  text   NOT NULL REFERENCES threads (id) ON DELETE CASCADE,
    status     text   NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    body       json   NOT NULL
);
CREATE INDEX runs_thread ON runs (thread_id, seq);
CREATE INDEX runs_status ON runs (status);

-- No foreign keys: events outlive the thread, run, assistant or tool they refer to.
CREATE TABLE audit_events (
    id                bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at                timestamptz NOT NULL,
    type              text        NOT NULL,
    thread_id         text,
    run_id            text,
    assistant_id      text,
    api_key_hash      text,
    model             text,
    latency_ms        bigint,
    prompt_tokens     integer,
    completion_tokens integer,
    total_tokens      integer,
    tool_name         text,
    outcome           text,
    error_code        text,
    detail            jsonb
);
CREATE INDEX audit_events_at ON audit_events (at);
CREATE INDEX audit_events_thread ON audit_events (thread_id, at);
CREATE INDEX audit_events_run ON audit_events (run_id);
