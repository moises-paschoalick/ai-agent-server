
export enum MessageRole {
  USER = 'user',
  ASSISTANT = 'assistant',
  TOOL_CALL = 'tool_call',
  TOOL_RESULT = 'tool_result',
  SYSTEM = 'system'
}

export interface ToolDefinition {
  id: string;
  name: string;
  description: string;
  inputStructure: string; // JSON string representation
  is_system?: boolean;
}

export interface Message {
  id: string;
  role: MessageRole;
  content: string;
  timestamp: number;
  toolDetails?: {
    toolName: string;
    toolCallId?: string;
    toolInput?: any;
    toolOutput?: string;
  };
  /** metadata of the backend answer (latency_ms, ttft_ms, total_tokens, model). */
  metadata?: Record<string, any>;
  /** Text still arriving (streamed answer). */
  streaming?: boolean;
  /** The answer failed after part of it was shown; the text so far is kept. */
  error?: string;
}

/** A sync thread as summarized by GET /threads/summaries; times in ms. */
export interface Thread {
  conversation_id: string;
  name: string; // title, or the short id when the thread has no user message yet
  title: string | null;
  assistantId: string | null;
  messageCount: number;
  createdAt: number;
  lastActivityAt: number;
}

/** Threads of one assistant in the thread menu; assistantId null is "no assistant". */
export interface ThreadGroup {
  assistantId: string | null;
  label: string;
  threads: Thread[];
}

export interface AssistantConfig {
  name: string;
  systemInstructions: string;
  model: string;
  tools: ToolDefinition[];
}

export interface ChatResponse {
  type?: 'tool_call';
  tool?: string;
  tool_call_id?: string;
  tool_input?: any;
  content?: string; // Standard text response if not a tool call
}

/** A /v1 assistant as listed: GET /v1/assistants. */
export interface AssistantSummary {
  id: string;
  name: string | null;
  model: string;
  toolCount: number;
  createdAt: number; // ms
}

export interface AssistantToolView {
  name: string;
  description?: string;
  parameters?: unknown;
}

/** A /v1 assistant with every field, read-only in the UI: GET /v1/assistants/{id}. */
export interface AssistantDetail extends AssistantSummary {
  description: string | null;
  instructions: string | null;
  tools: AssistantToolView[];
  metadata: Record<string, string>;
  temperature: number | null;
  topP: number | null;
  responseFormat: unknown;
  raw: unknown;
}

/** Fields of the assistant editor; numbers and JSON stay as text while being edited. */
export interface AssistantForm {
  name: string;
  description: string;
  model: string;
  instructions: string;
  temperature: string;
  topP: string;
  tools: string; // JSON: list in the OpenAI format
  metadata: string; // JSON object
}
