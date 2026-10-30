// Every call goes to /api on this origin; the proxy (Vite in dev, nginx in the container)
// forwards it to the backend and adds the API key, which never reaches the browser.
import { readSse } from './sse';

const BASE_URL = '/api';

/** Error answered by the backend, with its `error.message` when the body has one. */
export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

async function send(path: string, init: { method?: string; body?: unknown; signal?: AbortSignal }): Promise<Response> {
  const hasBody = init.body !== undefined;
  try {
    return await fetch(`${BASE_URL}${path}`, {
      method: init.method ?? 'GET',
      headers: hasBody ? { 'Content-Type': 'application/json' } : undefined,
      body: hasBody ? JSON.stringify(init.body) : undefined,
      ...(init.signal ? { signal: init.signal } : {}),
    });
  } catch (e) {
    if ((e as any)?.name === 'AbortError') {
      throw e;
    }
    throw new ApiError(0, 'Backend unreachable. Is it running?');
  }
}

async function readJson<T>(response: Response): Promise<T> {
  const text = await response.text();
  let json: any = undefined;
  try {
    json = text ? JSON.parse(text) : undefined;
  } catch {
    json = undefined;
  }
  if (!response.ok) {
    throw new ApiError(response.status, json?.error?.message ?? `HTTP ${response.status}`);
  }
  return json as T;
}

async function request<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  return readJson<T>(await send(path, init));
}

/** An `error` event of a stream: the answer failed after some text was already shown. */
export class StreamError extends ApiError {
  constructor(public readonly code: string, message: string) {
    super(200, message);
    this.name = 'StreamError';
  }
}

/**
 * POST with `stream: true`: `onDelta` gets each piece of text as it arrives; resolves with the
 * final `message` or `tool_call` (the same object as without streaming). An `error` event
 * rejects with StreamError; an error before the stream, with ApiError, as usual. `signal`
 * aborts the request, and the backend stops generating.
 */
async function streamRequest(path: string, body: object, onDelta: (text: string) => void, signal?: AbortSignal): Promise<any> {
  const response = await send(path, { method: 'POST', body: { ...body, stream: true }, signal });
  if (!response.ok || !(response.headers.get('content-type') ?? '').startsWith('text/event-stream') || !response.body) {
    return readJson<any>(response);
  }
  let final: { event: string; data: any } | undefined;
  await readSse(response.body, (event) => {
    if (event.event === 'delta') {
      onDelta(event.data?.content ?? '');
    } else {
      final = event;
    }
  });
  if (!final) {
    throw new StreamError('server_error', 'The answer ended before it was complete.');
  }
  if (final.event === 'error') {
    throw new StreamError(final.data?.code ?? 'server_error', final.data?.message ?? 'The answer failed.');
  }
  return final.data;
}

export const apiService = {
  createThread: () => request<{ conversation_id: string }>('/threads', { method: 'POST' }),

  getThreadSummaries: () => request<any[]>('/threads/summaries'),

  getThreadHistory: (threadId: string) =>
    request<{ role: string; content: string }[]>(`/threads/${encodeURIComponent(threadId)}/history`),

  deleteThread: (threadId: string) =>
    request<{ message: string }>(`/threads/${encodeURIComponent(threadId)}`, { method: 'DELETE' }),

  /** With an assistant, this message runs with its model, prompt and tools (as a /v1 run); the thread belongs to none. */
  sendMessage: (conversation_id: string, prompt: string, assistantId?: string | null) =>
    request<any>('/chat', {
      method: 'POST',
      body: { conversation_id, prompt, ...(assistantId ? { assistant_id: assistantId } : {}) },
    }),

  sendToolResult: (conversation_id: string, tool_name: string, tool_output: string, tool_call_id?: string) =>
    request<any>('/tools/result', {
      method: 'POST',
      body: { conversation_id, tool_name, tool_output, ...(tool_call_id ? { tool_call_id } : {}) },
    }),

  /** sendMessage answered in pieces (see streamRequest). */
  sendMessageStream: (conversation_id: string, prompt: string, assistantId: string | null | undefined,
                      onDelta: (text: string) => void, signal?: AbortSignal) =>
    streamRequest('/chat', { conversation_id, prompt, ...(assistantId ? { assistant_id: assistantId } : {}) }, onDelta, signal),

  /** sendToolResult answered in pieces (see streamRequest). */
  sendToolResultStream: (conversation_id: string, tool_name: string, tool_output: string, tool_call_id: string | undefined,
                         onDelta: (text: string) => void, signal?: AbortSignal) =>
    streamRequest('/tools/result', { conversation_id, tool_name, tool_output, ...(tool_call_id ? { tool_call_id } : {}) },
      onDelta, signal),

  getTools: () => request<any[]>('/tools'),

  /** Tool suggested by the model from a description ({name, description, parameters}); nothing is saved. */
  generateTool: (description: string, model?: string | null) =>
    request<{ name: string; description: string; parameters: any }>('/tools/generate', {
      method: 'POST',
      body: { description, ...(model ? { model } : {}) },
    }),

  registerTool: (toolDef: any) => request<any>('/tools', { method: 'POST', body: toolDef }),

  updateTool: (name: string, toolDef: any) =>
    request<any>(`/tools/${encodeURIComponent(name)}`, { method: 'PUT', body: toolDef }),

  deleteTool: (name: string) => request<any>(`/tools/${encodeURIComponent(name)}`, { method: 'DELETE' }),

  getSystemPrompt: () => request<{ system_prompt: string }>('/system-prompt'),

  updateSystemPrompt: (system_prompt: string) =>
    request<{ system_prompt: string }>('/system-prompt', { method: 'PUT', body: { system_prompt } }),

  getModels: () => request<string[]>('/models'),

  getCurrentModel: () => request<{ model: string }>('/model'),

  updateModel: (model: string) => request<{ model: string }>('/model', { method: 'PUT', body: { model } }),

  /** Every /v1 assistant, newest first: follows `after` while `has_more`. */
  listAssistants: async (): Promise<any[]> => {
    const all: any[] = [];
    let after: string | undefined;
    for (;;) {
      const page = await request<{ data: any[]; last_id: string | null; has_more: boolean }>(
        `/v1/assistants?limit=100&order=desc${after ? `&after=${encodeURIComponent(after)}` : ''}`,
      );
      all.push(...page.data);
      // Stops on a missing or repeated cursor too, so a wrong answer cannot loop forever
      if (!page.has_more || !page.last_id || page.last_id === after) {
        return all;
      }
      after = page.last_id;
    }
  },

  getAssistant: (id: string) => request<any>(`/v1/assistants/${encodeURIComponent(id)}`),

  createAssistant: (body: Record<string, unknown>) => request<any>('/v1/assistants', { method: 'POST', body }),

  /** Partial update: fields left out of the body keep their value. */
  updateAssistant: (id: string, body: Record<string, unknown>) =>
    request<any>(`/v1/assistants/${encodeURIComponent(id)}`, { method: 'POST', body }),

  deleteAssistant: (id: string) =>
    request<{ id: string; deleted: boolean }>(`/v1/assistants/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
