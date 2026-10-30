import { AssistantDetail, AssistantForm, AssistantSummary, AssistantToolView, Message, Thread, ThreadGroup, ToolDefinition } from '../types';

/** Short form of a thread id for labels: thread_abc...xyz → abc…xyz. */
export function shortId(id: string): string {
  const raw = id.replace(/^thread_/, '');
  return raw.length > 10 ? `${raw.slice(0, 4)}…${raw.slice(-4)}` : raw;
}

/** GET /threads/summaries: seconds become ms; the name falls back to the short id. */
export function toThreads(summaries: any[]): Thread[] {
  return summaries.map((s) => ({
    conversation_id: s.conversation_id,
    name: s.title || shortId(s.conversation_id),
    title: s.title ?? null,
    assistantId: s.assistant_id ?? null,
    messageCount: s.message_count ?? 0,
    createdAt: (s.created_at ?? 0) * 1000,
    lastActivityAt: (s.last_activity_at ?? s.created_at ?? 0) * 1000,
  }));
}

/**
 * Groups of the thread menu: the selected assistant first, then the others by their most recent
 * thread, and "no assistant" last. A deleted assistant is shown by its id. The query matches the
 * title or the id, ignoring case; groups left empty are dropped.
 */
export function groupThreads(
  threads: Thread[],
  assistants: { id: string; name: string | null }[],
  selectedId: string | null,
  query = '',
): ThreadGroup[] {
  const q = query.trim().toLowerCase();
  const matches = (t: Thread) =>
    !q || t.conversation_id.toLowerCase().includes(q) || (t.title ?? '').toLowerCase().includes(q);
  const names = new Map(assistants.map((a) => [a.id, a.name ?? '(sem nome)']));
  const byAssistant = new Map<string | null, Thread[]>();
  for (const t of threads.filter(matches)) {
    const list = byAssistant.get(t.assistantId) ?? [];
    list.push(t);
    byAssistant.set(t.assistantId, list);
  }
  const groups: ThreadGroup[] = [...byAssistant.entries()].map(([assistantId, list]) => ({
    assistantId,
    label: assistantId === null ? 'Sem assistant' : names.get(assistantId) ?? assistantId,
    threads: [...list].sort((a, b) => b.lastActivityAt - a.lastActivityAt),
  }));
  const rank = (g: ThreadGroup) => (g.assistantId === null ? 2 : g.assistantId === selectedId ? 0 : 1);
  return groups.sort((a, b) => rank(a) - rank(b) || b.threads[0].lastActivityAt - a.threads[0].lastActivityAt);
}

/** "agora", "5 min", "3 h", "2 d": time since a moment, for the thread menu. */
export function timeAgo(ms: number, now = Date.now()): string {
  const minutes = Math.floor((now - ms) / 60000);
  if (minutes < 1) return 'agora';
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h`;
  return `${Math.floor(hours / 24)} d`;
}

/**
 * Function tools of a /v1 assistant as the sidebar shows them. A tool named like a system tool is a
 * declaration of it: read-only here, changed with the editor's checkboxes.
 */
export function toAssistantToolDefinitions(tools: any[], systemNames: string[] = []): ToolDefinition[] {
  return (tools ?? [])
    .filter((t) => t?.type === 'function' && t.function?.name)
    .map((t) => ({
      id: t.function.name,
      name: t.function.name,
      description: t.function.description ?? '',
      inputStructure: JSON.stringify(t.function.parameters ?? { type: 'object', properties: {} }, null, 2),
      is_system: systemNames.includes(t.function.name),
    }));
}

/** System tools declared in the tools JSON of the editor; [] when the JSON is not a valid list. */
export function declaredSystemTools(toolsJson: string, systemNames: string[]): string[] {
  try {
    const tools = JSON.parse(toolsJson);
    if (!Array.isArray(tools)) return [];
    return systemNames.filter((name) => tools.some((t: any) => t?.type === 'function' && t.function?.name === name));
  } catch {
    return [];
  }
}

/**
 * Declares (on) or removes (off) a system tool in the tools JSON of the editor, keeping the other
 * tools. Invalid JSON is returned untouched, so the editor keeps showing its error.
 */
export function toggleSystemTool(toolsJson: string, name: string, on: boolean): string {
  let tools: any;
  try {
    tools = JSON.parse(toolsJson);
  } catch {
    return toolsJson;
  }
  if (!Array.isArray(tools)) return toolsJson;
  const others = tools.filter((t: any) => !(t?.type === 'function' && t.function?.name === name));
  return JSON.stringify(on ? [...others, { type: 'function', function: { name } }] : others, null, 2);
}

export type AppendToolResult = { tools: string } | { error: string };

/**
 * Adds a tool (JSON text in the OpenAI format) to the end of the tools JSON of the assistant editor.
 * Refuses, with the reason, invalid JSON on either side, a tool without a function name, or a name
 * already in the list.
 */
export function appendTool(toolsJson: string, toolJson: string): AppendToolResult {
  let tools: any;
  try {
    tools = JSON.parse(toolsJson.trim() === '' ? '[]' : toolsJson);
  } catch {
    return { error: 'Fix the tools JSON before adding a tool.' };
  }
  if (!Array.isArray(tools)) return { error: 'The tools JSON must be an array.' };
  let tool: any;
  try {
    tool = JSON.parse(toolJson);
  } catch {
    return { error: 'The generated tool is not valid JSON.' };
  }
  const name = tool?.function?.name;
  if (tool?.type !== 'function' || typeof name !== 'string' || !name.trim()) {
    return { error: 'The tool needs {"type":"function","function":{"name":...}}.' };
  }
  if (tools.some((t: any) => t?.function?.name === name)) {
    return { error: `A tool named "${name}" already exists.` };
  }
  return { tools: JSON.stringify([...tools, tool], null, 2) };
}

/** Tool of the sidebar modal as a /v1 assistant tool (OpenAI format). */
export function toAssistantTool(name: string, description: string, parameters: unknown) {
  return { type: 'function', function: { name, description, parameters } };
}

/** GET /tools: system tools (kind "system") run on the server and are read-only in the UI. */
export function toTools(views: any[]): ToolDefinition[] {
  return views.map((t) => ({
    id: t.name,
    name: t.name,
    description: t.description ?? '',
    inputStructure: JSON.stringify(t.parameters ?? {}, null, 2),
    is_system: t.kind === 'system',
  }));
}

/** Options of the model selector: the listed models, plus the current one when it is not listed. */
export function modelOptions(models: string[], current: string): string[] {
  return current && !models.includes(current) ? [current, ...models] : models;
}

/** GET /v1/assistants item: created_at comes in seconds. */
export function toAssistantSummary(a: any): AssistantSummary {
  return {
    id: a.id,
    name: a.name ?? null,
    model: a.model,
    toolCount: (a.tools ?? []).length,
    createdAt: (a.created_at ?? 0) * 1000,
  };
}

/** GET /v1/assistants/{id}: every field, plus the JSON as received. */
export function toAssistantDetail(a: any): AssistantDetail {
  const tools: AssistantToolView[] = (a.tools ?? []).map((t: any) =>
    t.type === 'function' && t.function
      ? { name: t.function.name, description: t.function.description ?? undefined, parameters: t.function.parameters ?? undefined }
      : { name: t.type },
  );
  return {
    ...toAssistantSummary(a),
    description: a.description ?? null,
    instructions: a.instructions ?? null,
    tools,
    metadata: a.metadata ?? {},
    temperature: a.temperature ?? null,
    topP: a.top_p ?? null,
    responseFormat: a.response_format ?? null,
    raw: a,
  };
}

/** Editor fields of an existing assistant (or an empty one with the given model). */
export function toAssistantForm(a: any | null, defaultModel = ''): AssistantForm {
  return {
    name: a?.name ?? '',
    description: a?.description ?? '',
    model: a?.model ?? defaultModel,
    instructions: a?.instructions ?? '',
    temperature: a?.temperature == null ? '' : String(a.temperature),
    topP: a?.top_p == null ? '' : String(a.top_p),
    tools: JSON.stringify(a?.tools ?? [], null, 2),
    metadata: JSON.stringify(a?.metadata ?? {}, null, 2),
  };
}

export type AssistantBodyResult =
  | { ok: true; body: Record<string, unknown> }
  | { ok: false; field: keyof AssistantForm; message: string };

/**
 * Body of POST /v1/assistants (or /{id}) from the editor. Only the JSON shape is checked here;
 * the backend validates the rest. Empty text and numbers are sent as null. response_format is
 * never sent, so an update keeps it.
 */
export function fromAssistantForm(form: AssistantForm): AssistantBodyResult {
  const text = (v: string) => (v.trim() === '' ? null : v);
  const number = (field: 'temperature' | 'topP'): number | null | undefined => {
    const v = form[field].trim();
    if (v === '') return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
  };
  const temperature = number('temperature');
  if (temperature === undefined) return { ok: false, field: 'temperature', message: 'temperature must be a number' };
  const topP = number('topP');
  if (topP === undefined) return { ok: false, field: 'topP', message: 'top_p must be a number' };

  let tools: unknown;
  try {
    tools = JSON.parse(form.tools.trim() || '[]');
  } catch (e) {
    return { ok: false, field: 'tools', message: `tools is not valid JSON: ${(e as Error).message}` };
  }
  if (!Array.isArray(tools)) return { ok: false, field: 'tools', message: 'tools must be a JSON array' };

  let metadata: unknown;
  try {
    metadata = JSON.parse(form.metadata.trim() || '{}');
  } catch (e) {
    return { ok: false, field: 'metadata', message: `metadata is not valid JSON: ${(e as Error).message}` };
  }
  if (metadata === null || typeof metadata !== 'object' || Array.isArray(metadata)) {
    return { ok: false, field: 'metadata', message: 'metadata must be a JSON object' };
  }

  return {
    ok: true,
    body: {
      name: text(form.name),
      description: text(form.description),
      model: form.model,
      instructions: text(form.instructions),
      temperature,
      top_p: topP,
      tools,
      metadata,
    },
  };
}

/** Appends a piece of streamed text to the message `id`. */
export function applyDelta(messages: Message[], id: string, text: string): Message[] {
  return messages.map(m => (m.id === id ? { ...m, content: m.content + text } : m));
}

/**
 * Ends the streamed message `id` with the final reply: a `message` replaces its text and adds the
 * metadata; a `tool_call` drops it when no text came (the tool call is shown on its own).
 */
export function finishStream(messages: Message[], id: string, reply: any): Message[] {
  if (reply?.type === 'tool_call') {
    return messages.flatMap(m => (m.id !== id ? [m] : m.content ? [{ ...m, streaming: false }] : []));
  }
  return messages.map(m => (m.id === id
    ? { ...m, content: reply?.content ?? m.content, metadata: reply?.metadata, streaming: false }
    : m));
}

/** Marks the streamed message `id` as failed, keeping what was already shown; drops it when empty and there is no error to show. */
export function failStream(messages: Message[], id: string, error?: string): Message[] {
  return messages.flatMap(m => {
    if (m.id !== id) {
      return [m];
    }
    if (!error) {
      return m.content ? [{ ...m, streaming: false }] : [];
    }
    return [{ ...m, streaming: false, error }];
  });
}

