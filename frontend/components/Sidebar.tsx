import React, { useEffect, useState } from 'react';
import { AssistantConfig, ToolDefinition } from '../types';
import { Plus, Settings, Trash2, Save, FileJson, Lock, Eye, Maximize2, PanelLeftClose } from 'lucide-react';
import JsonEditor from './JsonEditor';
import Modal from './Modal';
import ToolGenerator from './ToolGenerator';
import { modelOptions, toAssistantTool, toAssistantToolDefinitions } from '../services/mappers';

interface SidebarProps {
  config: AssistantConfig;
  setConfig: React.Dispatch<React.SetStateAction<AssistantConfig>>;
  /** The assistant picker, rendered at the top. */
  picker: React.ReactNode;
  /** Full /v1 assistant selected, or null for the global settings. */
  assistant: any | null;
  onUpdateAssistant: (patch: Record<string, unknown>) => Promise<void>;
  onRegisterTool: (tool: ToolDefinition) => void;
  onUpdateTool: (name: string, toolDef: any) => Promise<void>;
  onDeleteTool: (name: string) => Promise<void>;
  onSaveSystemPrompt: (prompt: string) => Promise<void>;
  availableModels: string[];
  onModelChange: (model: string) => Promise<void>;
  onClose: () => void;
  /** Answers shown as they are generated (server-sent events) or whole. */
  streamResponses: boolean;
  onStreamResponsesChange: (on: boolean) => void;
}

const Sidebar: React.FC<SidebarProps> = ({
  config,
  setConfig,
  picker,
  assistant,
  onUpdateAssistant,
  onRegisterTool,
  onUpdateTool,
  onDeleteTool,
  onSaveSystemPrompt,
  availableModels,
  onModelChange,
  onClose,
  streamResponses,
  onStreamResponsesChange,
}) => {
  const [isToolModalOpen, setIsToolModalOpen] = useState(false);
  const [editingToolId, setEditingToolId] = useState<string | null>(null);
  const [isReadOnly, setIsReadOnly] = useState(false);
  const [toolForm, setToolForm] = useState<{ name: string; description: string; inputStructure: string }>({
    name: '',
    description: '',
    inputStructure: '{}'
  });
  const [isSystemInstructionsModalOpen, setIsSystemInstructionsModalOpen] = useState(false);
  const [tempSystemInstructions, setTempSystemInstructions] = useState('');

  // With an assistant selected, model, instructions and tools are its own and are saved to it
  const [draftInstructions, setDraftInstructions] = useState('');
  useEffect(() => {
    setDraftInstructions(assistant?.instructions ?? '');
  }, [assistant?.id, assistant?.instructions]);

  const model = assistant ? assistant.model : config.model;
  const instructions = assistant ? draftInstructions : config.systemInstructions;
  const setInstructions = (value: string) =>
    assistant ? setDraftInstructions(value) : setConfig({ ...config, systemInstructions: value });
  const systemNames = config.tools.filter(t => t.is_system).map(t => t.name);
  // A declared system tool is shown with the server's definition, which is the one the model gets
  const tools: ToolDefinition[] = assistant
    ? toAssistantToolDefinitions(assistant.tools, systemNames).map(t => (t.is_system ? config.tools.find(c => c.name === t.name) ?? t : t))
    : config.tools;

  const saveInstructions = () =>
    assistant
      ? onUpdateAssistant({ instructions: draftInstructions.trim() === '' ? null : draftInstructions })
      : onSaveSystemPrompt(config.systemInstructions);

  const changeModel = (value: string) => {
    if (assistant) {
      onUpdateAssistant({ model: value });
    } else {
      setConfig({ ...config, model: value });
      onModelChange(value);
    }
  };

  /** Replaces (or adds) one function tool of the assistant, keeping the others as they are. */
  const saveAssistantTool = (name: string, description: string, parameters: unknown) => {
    const current: any[] = assistant.tools ?? [];
    const tool = toAssistantTool(name, description, parameters);
    const exists = current.some((t) => t?.function?.name === name);
    return onUpdateAssistant({
      tools: exists ? current.map((t) => (t?.function?.name === name ? tool : t)) : [...current, tool],
    });
  };

  const deleteTool = (name: string) => {
    if (!assistant) {
      onDeleteTool(name);
      return;
    }
    if (!confirm(`Remove tool "${name}" from this assistant?`)) return;
    onUpdateAssistant({ tools: (assistant.tools ?? []).filter((t: any) => t?.function?.name !== name) });
  };

  const openToolModal = (tool?: ToolDefinition) => {
    if (tool) {
      setEditingToolId(tool.id);
      setIsReadOnly(!!tool.is_system);
      setToolForm({
        name: tool.name,
        description: tool.description,
        inputStructure: tool.inputStructure
      });
    } else {
      setEditingToolId(null);
      setIsReadOnly(false);
      setToolForm({
        name: '',
        description: '',
        inputStructure: JSON.stringify({
          "type": "object",
          "properties": {},
          "required": []
        }, null, 2)
      });
    }
    setIsToolModalOpen(true);
  };

  const handleSaveTool = async () => {
    try {
      const parameters = JSON.parse(toolForm.inputStructure);

      if (assistant) {
        if (!toolForm.name.trim()) {
          alert('The tool needs a name.');
          return;
        }
        await saveAssistantTool(toolForm.name.trim(), toolForm.description, parameters);
      } else if (editingToolId) {
        // Updating existing tool
        const toolDef = {
          name: toolForm.name,
          description: toolForm.description,
          parameters: parameters,
          strict: false // Default strict to false for now, could add UI for it later
        };
        await onUpdateTool(toolForm.name, toolDef);

        // Update local state
        setConfig(prev => ({
          ...prev,
          tools: prev.tools.map(t => t.id === editingToolId ? { ...t, ...toolForm } : t)
        }));
      } else {
        // Creating new tool
        // Ideally we should call a backend create endpoint which returns the created tool, but current flow registers existing tools.
        // We will simulate creation by adding to local state and calling onRegisterTool (which does the POST).
        // Actually, onRegisterTool (api.registerTool) is what we want.

        const newTool: ToolDefinition = {
          id: Date.now().toString(),
          name: toolForm.name,
          description: toolForm.description,
          inputStructure: toolForm.inputStructure
        };

        // Validate JSON before proceeding
        JSON.parse(toolForm.inputStructure);

        // Call backend to register/create
        onRegisterTool(newTool);

        // Update local state if we want to show it immediately (though onRegisterTool implementation in App.tsx might need checking if it refreshes config)
        // App.tsx handleRegisterTool refreshes config, so strictly we might not need to setConfig manually if we trust the refresh.
        // But let's look at App.tsx again. It calls fetchConfig().
        // So we can just close the modal.
      }
      setIsToolModalOpen(false);
    } catch (e) {
      alert('Invalid JSON in parameters: ' + (e as Error).message);
    }
  };

  return (
    <div className="w-80 shrink-0 bg-[#171717] border-r border-[#2d2d2d] flex flex-col overflow-hidden">
      <div className="p-4 border-b border-[#2d2d2d] flex items-center justify-between">
        <div className="flex items-center space-x-2">
          <Settings className="w-5 h-5 text-emerald-500" />
          <h2 className="text-lg font-bold">Configuração</h2>
        </div>
        <button onClick={onClose} title="Close panel" className="p-1.5 rounded transition text-gray-400 hover:bg-[#2d2d2d]">
          <PanelLeftClose className="w-4 h-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* Basic Config */}
        <section className="space-y-4">
          {/* /v1 assistants: the fields below show and save the selected one (or the global settings) */}
          {picker}
          <p className="text-[10px] text-gray-500 -mt-2">
            {assistant ? 'Model, instructions and tools below belong to this assistant.' : 'Global settings, used by threads without an assistant.'}
          </p>
          <div>
            <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">Model</label>
            <select
              value={model}
              onChange={(e) => changeModel(e.target.value)}
              className="w-full bg-[#212121] border border-[#2d2d2d] rounded-md px-3 py-2 focus:ring-1 focus:ring-emerald-500 outline-none text-sm font-mono appearance-none"
            >
              {modelOptions(availableModels, model).map(m => (
                <option key={m} value={m}>{m}</option>
              ))}
            </select>
          </div>
          <label className="flex items-center justify-between gap-2 text-xs text-gray-400 cursor-pointer select-none">
            <span className="font-semibold uppercase tracking-wider">Stream responses</span>
            <input
              type="checkbox"
              checked={streamResponses}
              onChange={(e) => onStreamResponsesChange(e.target.checked)}
              className="accent-emerald-500 w-4 h-4"
            />
          </label>
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider">System Instructions</label>
              <div className="flex items-center space-x-2">
                <button
                  onClick={() => {
                    setTempSystemInstructions(instructions);
                    setIsSystemInstructionsModalOpen(true);
                  }}
                  className="flex items-center space-x-1 text-xs text-blue-500 hover:text-blue-400 transition bg-[#212121] px-2 py-1 rounded border border-[#2d2d2d] hover:border-blue-500/50"
                  title="Open in Modal"
                >
                  <Maximize2 className="w-3 h-3" />
                  <span>Expand</span>
                </button>
                <button
                  onClick={saveInstructions}
                  className="flex items-center space-x-1 text-xs text-emerald-500 hover:text-emerald-400 transition bg-[#212121] px-2 py-1 rounded border border-[#2d2d2d] hover:border-emerald-500/50"
                  title={assistant ? 'Save to the assistant' : 'Save to Backend'}
                >
                  <Save className="w-3 h-3" />
                  <span>Save</span>
                </button>
              </div>
            </div>
            <textarea
              rows={8}
              value={instructions}
              onChange={(e) => setInstructions(e.target.value)}
              className="w-full bg-[#212121] border border-[#2d2d2d] rounded-md px-3 py-2 focus:ring-1 focus:ring-emerald-500 outline-none text-sm resize-y"
              placeholder="Define rules for the assistant..."
            />
          </div>
        </section>

        {/* Tools Section */}
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Tools</label>
            <button
              onClick={() => openToolModal()}
              className="p-1 hover:bg-[#2d2d2d] rounded text-emerald-500"
              title="Add New Tool"
            >
              <Plus className="w-4 h-4" />
            </button>
          </div>
          <div className="space-y-1">
            {tools.length === 0 && (
              <p className="text-xs text-gray-500 italic px-2">No tools defined.</p>
            )}
            {tools.map((tool) => (
              <div key={tool.id} className="group relative flex items-center justify-between p-2 rounded hover:bg-[#212121] border border-transparent hover:border-[#2d2d2d]">
                <div className="flex items-center space-x-2 overflow-hidden">
                  <FileJson className="w-4 h-4 text-gray-500" />
                  <span className="text-sm text-gray-300 font-mono truncate">{tool.name}</span>
                </div>
                <div className="flex items-center space-x-1 opacity-0 group-hover:opacity-100 transition">
                  {tool.is_system ? (
                    <div className="flex items-center space-x-1">
                      <button
                        onClick={() => openToolModal(tool)}
                        className="p-1 text-gray-500 hover:text-blue-400"
                        title="View Tool"
                      >
                        <Eye className="w-3.5 h-3.5" />
                      </button>
                      <span className="p-1 text-gray-600" title="System Tool (Read-only)">
                        <Lock className="w-3.5 h-3.5" />
                      </span>
                    </div>
                  ) : (
                    <>
                      <button
                        onClick={() => openToolModal(tool)}
                        className="p-1 text-gray-500 hover:text-blue-400"
                        title="Edit Tool"
                      >
                        <Settings className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => deleteTool(tool.name)}
                        className="p-1 text-gray-500 hover:text-red-400"
                        title="Delete Tool"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Tool Modal */}
        <Modal
          isOpen={isToolModalOpen}
          onClose={() => setIsToolModalOpen(false)}
          title={isReadOnly ? "View Tool" : (editingToolId ? "Edit Tool" : "Create New Tool")}
        >
          <div className="space-y-4">
            {!editingToolId && !isReadOnly && (
              // The model of the selected assistant, or the global one; the result stays editable below
              <ToolGenerator
                model={model}
                onGenerated={(tool) =>
                  setToolForm({
                    name: tool.name,
                    description: tool.description ?? '',
                    inputStructure: JSON.stringify(tool.parameters ?? {}, null, 2)
                  })
                }
              />
            )}
            <div>
              <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">Name</label>
              <input
                type="text"
                value={toolForm.name}
                onChange={(e) => setToolForm({ ...toolForm, name: e.target.value })}
                className={`w-full bg-[#212121] border border-[#2d2d2d] rounded-md px-3 py-2 focus:ring-1 focus:ring-emerald-500 outline-none text-sm font-mono ${isReadOnly ? 'opacity-50 cursor-default' : ''}`}
                placeholder="search_web"
                disabled={!!editingToolId || isReadOnly} // Prevent renaming existing tools
              />
              {editingToolId && !isReadOnly && <p className="text-[10px] text-gray-500 mt-1">Tool names cannot be changed once created.</p>}
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">Description</label>
              <textarea
                value={toolForm.description}
                onChange={(e) => setToolForm({ ...toolForm, description: e.target.value })}
                className={`w-full bg-[#212121] border border-[#2d2d2d] rounded-md px-3 py-2 focus:ring-1 focus:ring-emerald-500 outline-none text-sm resize-none h-20 ${isReadOnly ? 'opacity-50 cursor-default' : ''}`}
                placeholder="Describes what this tool does..."
                readOnly={isReadOnly}
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">Parameters (JSON Schema)</label>
              <JsonEditor
                value={toolForm.inputStructure}
                onChange={(val) => setToolForm({ ...toolForm, inputStructure: val })}
                height="h-64"
                readOnly={isReadOnly}
              />
            </div>
            <div className="flex items-center justify-end space-x-3 pt-4 border-t border-[#2d2d2d]">
              <button
                onClick={() => setIsToolModalOpen(false)}
                className="px-4 py-2 text-sm text-gray-400 hover:text-white transition"
              >
                {isReadOnly ? 'Close' : 'Cancel'}
              </button>
              {!isReadOnly && (
                <button
                  onClick={handleSaveTool}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-bold rounded shadow-lg transition flex items-center space-x-2"
                >
                  <Save className="w-4 h-4" />
                  <span>{editingToolId ? 'Update Tool' : 'Create Tool'}</span>
                </button>
              )}
            </div>
          </div>
        </Modal>

        {/* System Instructions Modal */}
        <Modal
          isOpen={isSystemInstructionsModalOpen}
          onClose={() => setIsSystemInstructionsModalOpen(false)}
          title="Edit System Instructions"
        >
          <div className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">Instructions</label>
              <textarea
                value={tempSystemInstructions}
                onChange={(e) => setTempSystemInstructions(e.target.value)}
                className="w-full bg-[#212121] border border-[#2d2d2d] rounded-md px-3 py-2 focus:ring-1 focus:ring-emerald-500 outline-none text-sm resize-y h-96"
                placeholder="Define rules for the assistant..."
              />
            </div>
            <div className="flex items-center justify-end space-x-3 pt-4 border-t border-[#2d2d2d]">
              <button
                onClick={() => setIsSystemInstructionsModalOpen(false)}
                className="px-4 py-2 text-sm text-gray-400 hover:text-white transition"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  setInstructions(tempSystemInstructions);
                  setIsSystemInstructionsModalOpen(false);
                }}
                className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-bold rounded shadow-lg transition flex items-center space-x-2"
              >
                <Save className="w-4 h-4" />
                <span>Apply Changes</span>
              </button>
            </div>
          </div>
        </Modal>

      </div >
    </div >
  );
};

export default Sidebar;
