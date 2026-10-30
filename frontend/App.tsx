
import React, { useState, useEffect, useCallback, useRef } from 'react';
import Sidebar from './components/Sidebar';
import ChatContainer from './components/ChatContainer';
import Toast from './components/Toast';
import HistoryView from './components/HistoryView';
import AssistantPicker from './components/AssistantPicker';
import ThreadMenu from './components/ThreadMenu';
import { useAssistants } from './hooks/useAssistants';
import { AssistantConfig, Message, MessageRole, Thread, ToolDefinition } from './types';
import { apiService, StreamError } from './services/api';
import { applyDelta, failStream, finishStream, modelOptions, toThreads, toTools } from './services/mappers';

const STREAM_KEY = 'agentServer.streamResponses';

/** Message of the backend error (error.message) when there is one. */
const errorMessage = (err: unknown, fallback: string) =>
  err instanceof Error && err.message ? err.message : fallback;

const App: React.FC = () => {
  // Assistant Settings
  const [config, setConfig] = useState<AssistantConfig>(() => {
    const saved = localStorage.getItem('assistant_config');
    return saved ? JSON.parse(saved) : {
      name: 'Default Assistant',
      systemInstructions: 'You are a helpful assistant.',
      model: 'gpt-4o',
      tools: []
    };
  });

  // Application State
  const [threads, setThreads] = useState<Thread[]>([]);
  const [currentThreadId, setCurrentThreadId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isDebugMode, setIsDebugMode] = useState(false);
  // Stream answers as they are generated (remembered in this browser; on by default)
  const [streamResponses, setStreamResponses] = useState<boolean>(() => {
    try {
      return localStorage.getItem(STREAM_KEY) !== 'false';
    } catch {
      return true;
    }
  });
  const streamAbort = useRef<AbortController | null>(null);

  const toggleStreamResponses = (on: boolean) => {
    setStreamResponses(on);
    try {
      localStorage.setItem(STREAM_KEY, String(on));
    } catch {
      // storage blocked: the choice lasts until the page reloads
    }
  };

  // Leaving the thread stops an answer still streaming; the backend then stops generating it
  const stopStream = () => {
    streamAbort.current?.abort();
    streamAbort.current = null;
  };

  // History View State
  const [showHistory, setShowHistory] = useState(false);
  const [historyThreadId, setHistoryThreadId] = useState<string | null>(null);
  const [historyMessages, setHistoryMessages] = useState<any[]>([]);

  // Models State
  const [availableModels, setAvailableModels] = useState<string[]>([]);

  // /v1 assistants: the selected one scopes the sidebar fields and new threads
  const assistants = useAssistants();

  // Left panel, remembered per browser
  const [isSidebarOpen, setIsSidebarOpen] = useState(() => {
    try {
      return localStorage.getItem('sidebar_open') !== 'false';
    } catch {
      return true;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem('sidebar_open', String(isSidebarOpen));
    } catch {
      // only a convenience
    }
  }, [isSidebarOpen]);

  // Sync config to local storage
  useEffect(() => {
    localStorage.setItem('assistant_config', JSON.stringify(config));
  }, [config]);

  // Load System Prompt from Backend
  useEffect(() => {
    const loadSystemPrompt = async () => {
      try {
        const { system_prompt } = await apiService.getSystemPrompt();
        setConfig(prev => ({ ...prev, systemInstructions: system_prompt }));
      } catch (err) {
        console.error("Failed to load system prompt", err);
      }
    };
    loadSystemPrompt();
  }, []);

  // Fetch tools helper function
  const fetchTools = useCallback(async () => {
    try {
      const tools: ToolDefinition[] = toTools(await apiService.getTools());
      setConfig(prev => ({ ...prev, tools }));
    } catch (err) {
      console.error("Failed to fetch tools", err);
    }
  }, []);

  // Load models and tools independently: a failure in one does not hide the other
  useEffect(() => {
    const loadModels = async () => {
      let models: string[] = [];
      try {
        models = await apiService.getModels();
      } catch (err) {
        console.error("Failed to load models", err);
      }
      try {
        const { model } = await apiService.getCurrentModel();
        setAvailableModels(modelOptions(models, model));
        setConfig(prev => ({ ...prev, model }));
      } catch (err) {
        setAvailableModels(models);
        console.error("Failed to load current model", err);
      }
    };
    loadModels();
    fetchTools();
  }, [fetchTools]);

  const handleSaveSystemPrompt = async (prompt: string) => {
    try {
      await apiService.updateSystemPrompt(prompt);
      alert('System instructions saved successfully!');
    } catch (err) {
      setError(errorMessage(err, 'Failed to save system instructions.'));
    }
  };

  const handleModelChange = async (model: string) => {
    try {
      await apiService.updateModel(model);
    } catch (err) {
      setError(errorMessage(err, 'Failed to update model.'));
    }
  };

  const handleUpdateAssistant = async (patch: Record<string, unknown>) => {
    try {
      await assistants.update(patch);
    } catch (err) {
      setError(errorMessage(err, 'Failed to save the assistant.'));
    }
  };

  const handleUpdateTool = async (name: string, toolDef: any) => {
    try {
      await apiService.updateTool(name, toolDef);
      // Refresh tools list after update
      await fetchTools();
    } catch (err) {
      setError(errorMessage(err, 'Failed to update tool.'));
    }
  };

  const fetchThreads = useCallback(async () => {
    try {
      const mappedThreads: Thread[] = toThreads(await apiService.getThreadSummaries());
      setThreads(mappedThreads);
    } catch (err) {
      console.error("Failed to fetch threads", err);
      // Optional: don't show error to user immediately on mount
    }
  }, []);

  useEffect(() => {
    fetchThreads();
  }, [fetchThreads]);

  // "New thread" only clears the chat: the thread is created by the first message, so no empty
  // threads pile up in the menu
  const handleNewThread = () => {
    stopStream();
    setCurrentThreadId(null);
    setMessages([]);
  };

  // Creates the thread for the first message; null when it could not be created
  const createThread = async (): Promise<string | null> => {
    try {
      const { conversation_id } = await apiService.createThread();
      setCurrentThreadId(conversation_id);
      return conversation_id;
    } catch (err) {
      setError(errorMessage(err, 'Erro interno ao processar a requisição. Verifique o backend.'));
      return null;
    }
  };

  // Threads belong to no assistant (as in OpenAI); the menu groups them by the last one that
  // answered. Picking another assistant goes to the most recent thread it answered, or to an empty
  // new conversation
  const handleSelectAssistant = (id: string | null) => {
    assistants.select(id);
    const current = threads.find(t => t.conversation_id === currentThreadId);
    if (currentThreadId && current?.assistantId === id) return;
    const latest = threads
      .filter(t => t.assistantId === id)
      .sort((a, b) => b.lastActivityAt - a.lastActivityAt)[0];
    if (latest) {
      handleSelectThread(latest.conversation_id);
    } else {
      stopStream();
      setCurrentThreadId(null);
      setMessages([]);
    }
  };

  // Opening a thread selects the assistant that answered it last, so the next message goes on with it
  const handleOpenThread = (thread: Thread) => {
    if (thread.assistantId === null) {
      assistants.select(null);
    } else if (assistants.assistants.some(a => a.id === thread.assistantId)) {
      assistants.select(thread.assistantId);
    }
    handleSelectThread(thread.conversation_id);
  };

  // Handle switching threads and loading history
  const handleSelectThread = async (threadId: string) => {
    try {
      setIsLoading(true);
      stopStream();
      setCurrentThreadId(threadId);
      const history = await apiService.getThreadHistory(threadId);
      // Map API history to internal Message interface
      const mappedHistory = history.map((h: any, idx: number) => ({
        id: `hist-${idx}`,
        role: h.role as MessageRole,
        content: h.content || h.prompt || '',
        timestamp: Date.now(), // Real backend should provide timestamp
        toolDetails: h.tool ? {
          toolName: h.tool,
          toolCallId: h.tool_call_id,
          toolInput: h.tool_input,
          toolOutput: h.tool_output
        } : undefined
      }));
      setMessages(mappedHistory);
    } catch (err) {
      setError(errorMessage(err, 'Erro ao carregar histórico da thread.'));
    } finally {
      setIsLoading(false);
    }
  };

  const handleDeleteThread = async (threadId: string) => {
    if (!confirm('Are you sure you want to delete this thread?')) return;
    try {
      await apiService.deleteThread(threadId);
      setThreads(prev => prev.filter(t => t.conversation_id !== threadId));
      fetchThreads();
      if (currentThreadId === threadId) {
        stopStream();
        setCurrentThreadId(null);
        setMessages([]);
      }
    } catch (err) {
      console.error('Failed to delete thread:', err);
      setError(errorMessage(err, 'Failed to delete thread'));
    }
  };

  const handleViewHistory = async (threadId: string) => {
    try {
      setHistoryThreadId(threadId);
      setShowHistory(true);
      const history = await apiService.getThreadHistory(threadId);
      setHistoryMessages(history);
    } catch (err) {
      setError(errorMessage(err, 'Erro ao carregar histórico completo.'));
    }
  };

  const handleRegisterTool = async (tool: ToolDefinition) => {
    try {
      const toolDef = {
        name: tool.name,
        description: tool.description,
        parameters: JSON.parse(tool.inputStructure),
        strict: false // Default to false or add to UI
      };
      await apiService.registerTool(toolDef);
      // Refresh tools list after registration
      await fetchTools();
      alert(`Tool ${tool.name} registered successfully!`);
    } catch (err) {
      setError(errorMessage(err, 'Erro ao registrar tool. Verifique o JSON.'));
    }
  };

  const handleDeleteTool = async (name: string) => {
    if (!confirm(`Are you sure you want to delete tool "${name}"?`)) return;
    try {
      await apiService.deleteTool(name);

      setConfig(prev => ({
        ...prev,
        tools: prev.tools.filter(t => t.name !== name)
      }));

      alert(`Tool ${name} deleted successfully!`);
    } catch (err) {
      console.error('Failed to delete tool:', err);
      setError(errorMessage(err, 'Failed to delete tool'));
    }
  };

  // Handle sending user message
  const handleSendMessage = async (prompt: string) => {
    // Without an open thread, the first message creates one
    const threadId = currentThreadId ?? await createThread();
    if (!threadId) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: MessageRole.USER,
      content: prompt,
      timestamp: Date.now()
    };

    setMessages(prev => [...prev, userMessage]);
    setIsLoading(true);

    try {
      // The selected assistant answers this message (null: the global settings)
      await answer(
        (onDelta, signal) => apiService.sendMessageStream(threadId, prompt, assistants.selectedId, onDelta, signal),
        () => apiService.sendMessage(threadId, prompt, assistants.selectedId),
        'Erro interno ao processar a requisição. Verifique o backend.');
    } finally {
      setIsLoading(false);
      fetchThreads();
    }
  };

  /**
   * Gets the answer streamed (text shown as it arrives) or whole, as the switch says. An error
   * after part of the text keeps that text and shows the error on the message itself.
   */
  const answer = async (
    streamed: (onDelta: (text: string) => void, signal: AbortSignal) => Promise<any>,
    whole: () => Promise<any>,
    fallback: string,
  ) => {
    if (!streamResponses) {
      try {
        handleApiResponse(await whole());
      } catch (err) {
        setError(errorMessage(err, fallback));
      }
      return;
    }
    const id = `assistant-${Date.now()}`;
    const controller = new AbortController();
    stopStream();
    streamAbort.current = controller;
    setMessages(prev => [...prev, { id, role: MessageRole.ASSISTANT, content: '', timestamp: Date.now(), streaming: true }]);
    try {
      const reply = await streamed(text => setMessages(prev => applyDelta(prev, id, text)), controller.signal);
      setMessages(prev => finishStream(prev, id, reply));
      if (reply?.type === 'tool_call') {
        handleApiResponse(reply);
      }
    } catch (err: any) {
      if (err?.name === 'AbortError') {
        setMessages(prev => failStream(prev, id));
      } else if (err instanceof StreamError) {
        setMessages(prev => failStream(prev, id, err.message));
      } else {
        setMessages(prev => failStream(prev, id));
        setError(errorMessage(err, fallback));
      }
    } finally {
      if (streamAbort.current === controller) {
        streamAbort.current = null;
      }
    }
  };

  // Process response from backend (could be text or tool call)
  const handleApiResponse = (response: any) => {
    if (response.type === 'tool_call') {
      const toolCallMessage: Message = {
        id: `tool-${Date.now()}`,
        role: MessageRole.TOOL_CALL,
        content: `Calling tool: ${response.tool}`,
        timestamp: Date.now(),
        toolDetails: {
          toolName: response.tool,
          toolCallId: response.tool_call_id,
          toolInput: response.tool_input
        }
      };
      setMessages(prev => [...prev, toolCallMessage]);
    } else {
      const assistantMessage: Message = {
        id: `assistant-${Date.now()}`,
        role: MessageRole.ASSISTANT,
        content: response.content || response.text || "Sem resposta do servidor.",
        timestamp: Date.now(),
        metadata: response.metadata
      };
      setMessages(prev => [...prev, assistantMessage]);
    }
  };

  // Handle tool result submission
  const handleToolSubmit = async (toolName: string, output: string, toolCallId?: string) => {
    if (!currentThreadId) return;

    const resultMessage: Message = {
      id: `res-${Date.now()}`,
      role: MessageRole.TOOL_RESULT,
      content: `Result: ${output}`,
      timestamp: Date.now(),
      toolDetails: {
        toolName,
        toolCallId,
        toolOutput: output
      }
    };

    setMessages(prev => [...prev, resultMessage]);
    setIsLoading(true);

    try {
      await answer(
        (onDelta, signal) => apiService.sendToolResultStream(currentThreadId, toolName, output, toolCallId, onDelta, signal),
        () => apiService.sendToolResult(currentThreadId, toolName, output, toolCallId),
        'Erro ao enviar resultado da tool.');
    } finally {
      setIsLoading(false);
      fetchThreads();
    }
  };

  return (
    <div className="flex h-screen overflow-hidden bg-[#0b0b0b] relative">
      {/* Sidebar - Config: slides out to the left; inert while closed so it is not tabbable */}
      <div
        className={`shrink-0 flex overflow-hidden transition-[width] duration-300 ease-in-out ${isSidebarOpen ? 'w-80' : 'w-0'}`}
        inert={!isSidebarOpen}
      >
      <Sidebar
        config={config}
        setConfig={setConfig}
        picker={
          <AssistantPicker
            models={availableModels}
            assistants={assistants.assistants}
            selectedId={assistants.selectedId}
            isLoading={assistants.isLoading}
            error={assistants.error}
            onSelect={handleSelectAssistant}
            onReload={assistants.reload}
            onSaved={assistants.saved}
          />
        }
        assistant={assistants.assistant}
        onUpdateAssistant={handleUpdateAssistant}
        onRegisterTool={handleRegisterTool}
        onUpdateTool={handleUpdateTool}
        onDeleteTool={handleDeleteTool}
        onSaveSystemPrompt={handleSaveSystemPrompt}
        streamResponses={streamResponses}
        onStreamResponsesChange={toggleStreamResponses}
        availableModels={availableModels}
        onModelChange={handleModelChange}
        onClose={() => setIsSidebarOpen(false)}
      />
      </div>

      {/* Main Content - Chat */}
      <div className="flex-1 flex flex-col relative">
        <ChatContainer
          messages={messages}
          onSendMessage={handleSendMessage}
          onToolSubmit={handleToolSubmit}
          isLoading={isLoading}
          currentThreadId={currentThreadId}
          isDebugMode={isDebugMode}
          onToggleDebug={() => setIsDebugMode(!isDebugMode)}
          threadMenu={
            <ThreadMenu
              threads={threads}
              assistants={assistants.assistants}
              selectedAssistantId={assistants.selectedId}
              currentThreadId={currentThreadId}
              onOpen={handleOpenThread}
              onNew={handleNewThread}
              onDelete={handleDeleteThread}
              onViewHistory={handleViewHistory}
              onRefresh={fetchThreads}
            />
          }
          onOpenSidebar={isSidebarOpen ? undefined : () => setIsSidebarOpen(true)}
        />
      </div>

      {/* History View Sidebar */}
      {showHistory && historyThreadId && (
        <HistoryView
          threadId={historyThreadId}
          history={historyMessages}
          onClose={() => setShowHistory(false)}
        />
      )}

      {/* Error Toasts */}
      {error && <Toast message={error} onClose={() => setError(null)} />}
    </div>
  );
};

export default App;
