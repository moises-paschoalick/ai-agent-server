import React, { useEffect, useState } from 'react';
import { ChevronDown, ChevronRight, Copy, Lock, Plus, Save, Sparkles } from 'lucide-react';
import Modal from './Modal';
import JsonEditor from './JsonEditor';
import ToolGenerator from './ToolGenerator';
import { AssistantForm } from '../types';
import { apiService } from '../services/api';
import { appendTool, declaredSystemTools, fromAssistantForm, modelOptions, toAssistantForm, toAssistantTool, toggleSystemTool } from '../services/mappers';

interface AssistantEditorProps {
  isOpen: boolean;
  /** The /v1 assistant to edit, or null to create one. */
  assistant: any | null;
  models: string[];
  onClose: () => void;
  onSaved: (assistant: any) => void;
}

const inputClass =
  'w-full bg-[#212121] border border-[#2d2d2d] rounded-md px-3 py-2 focus:ring-1 focus:ring-emerald-500 outline-none text-sm';

const Label: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">{children}</label>
);

/** Create or edit a /v1 assistant. Backend errors stay in the modal, with what was typed. */
const AssistantEditor: React.FC<AssistantEditorProps> = ({ isOpen, assistant, models, onClose, onSaved }) => {
  const [form, setForm] = useState<AssistantForm>(() => toAssistantForm(assistant, models[0] ?? ''));
  const [error, setError] = useState<{ field?: keyof AssistantForm; message: string } | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [copied, setCopied] = useState(false);
  const [showRaw, setShowRaw] = useState(false);
  // Generate tool panel: the generated tool (OpenAI format, editable) waits here until Add
  const [showGenerator, setShowGenerator] = useState(false);
  const [generatedTool, setGeneratedTool] = useState('');
  const [generatorError, setGeneratorError] = useState<string | null>(null);
  // System tools of the server (GET /tools, kind "system"): declared as {type:function, function:{name}}
  const [systemTools, setSystemTools] = useState<{ name: string; description: string }[]>([]);

  useEffect(() => {
    if (!isOpen) return;
    apiService
      .getTools()
      .then((tools) => setSystemTools(tools.filter((t) => t.kind === 'system').map((t) => ({ name: t.name, description: t.description ?? '' }))))
      .catch(() => setSystemTools([]));
  }, [isOpen]);

  useEffect(() => {
    if (isOpen) {
      setForm(toAssistantForm(assistant, models[0] ?? ''));
      setError(null);
      setCopied(false);
      setShowRaw(false);
      setShowGenerator(false);
      setGeneratedTool('');
      setGeneratorError(null);
    }
  }, [isOpen, assistant]); // not on models: a reload of the list must not wipe what is being typed

  const set = (field: keyof AssistantForm) => (value: string) => setForm((f) => ({ ...f, [field]: value }));

  /** Adds the generated tool to the tools JSON of the form; the assistant changes only on Save. */
  const addGeneratedTool = () => {
    const result = appendTool(form.tools, generatedTool);
    if ('error' in result) {
      setGeneratorError(result.error);
      return;
    }
    set('tools')(result.tools);
    setGeneratedTool('');
    setGeneratorError(null);
    setShowGenerator(false);
  };

  const save = async () => {
    const result = fromAssistantForm(form);
    if (result.ok === false) {
      setError({ field: result.field, message: result.message });
      return;
    }
    setIsSaving(true);
    setError(null);
    try {
      const saved = assistant
        ? await apiService.updateAssistant(assistant.id, result.body)
        : await apiService.createAssistant(result.body);
      onSaved(saved);
    } catch (err) {
      setError({ message: err instanceof Error && err.message ? err.message : 'Failed to save the assistant.' });
    } finally {
      setIsSaving(false);
    }
  };

  const copyId = async () => {
    try {
      await navigator.clipboard.writeText(assistant.id);
      setCopied(true);
    } catch {
      setError({ message: 'The browser did not allow copying the id.' });
    }
  };

  const fieldError = (field: keyof AssistantForm) =>
    error?.field === field ? <p className="text-[11px] text-red-400 mt-1">{error.message}</p> : null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={assistant ? 'Edit Assistant' : 'Create Assistant'}>
      <div className="space-y-4">
        {assistant && (
          <div className="flex items-center gap-2 text-xs">
            <code className="font-mono text-gray-400 break-all">{assistant.id}</code>
            <button onClick={copyId} className="text-gray-400 hover:text-white shrink-0" title="Copy id">
              <Copy className="w-4 h-4" />
            </button>
            {copied && <span className="text-[10px] text-emerald-400">copied</span>}
          </div>
        )}

        {error && !error.field && (
          <div className="text-sm text-red-400 bg-red-500/10 border border-red-500/30 rounded p-3">{error.message}</div>
        )}

        <div className="grid grid-cols-2 gap-4">
          <div>
            <Label>Name</Label>
            <input className={inputClass} value={form.name} onChange={(e) => set('name')(e.target.value)} placeholder="Ex: Weather" />
          </div>
          <div>
            <Label>Model</Label>
            <select className={`${inputClass} font-mono`} value={form.model} onChange={(e) => set('model')(e.target.value)}>
              {modelOptions(models, form.model).map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <Label>Description</Label>
          <input className={inputClass} value={form.description} onChange={(e) => set('description')(e.target.value)} />
        </div>

        <div>
          <Label>Instructions</Label>
          <textarea
            rows={8}
            className={`${inputClass} resize-y font-mono text-xs`}
            value={form.instructions}
            onChange={(e) => set('instructions')(e.target.value)}
            placeholder="System prompt of the assistant..."
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <Label>Temperature</Label>
            <input className={inputClass} value={form.temperature} onChange={(e) => set('temperature')(e.target.value)} placeholder="default" />
            {fieldError('temperature')}
          </div>
          <div>
            <Label>Top P</Label>
            <input className={inputClass} value={form.topP} onChange={(e) => set('topP')(e.target.value)} placeholder="default" />
            {fieldError('topP')}
          </div>
        </div>

        {systemTools.length > 0 && (
          <div>
            <Label>System tools</Label>
            <p className="text-[11px] text-gray-500 mb-2">Run on the server. With AGENT_SYSTEM_TOOLS_MODE=declared, only the checked ones are offered.</p>
            {(() => {
              const names = systemTools.map((t) => t.name);
              const declared = declaredSystemTools(form.tools, names);
              let valid = true;
              try {
                valid = Array.isArray(JSON.parse(form.tools));
              } catch {
                valid = false;
              }
              return (
                <div className="grid grid-cols-2 gap-2">
                  {systemTools.map((t) => (
                    <label key={t.name} className={`flex items-start gap-2 text-xs ${valid ? 'cursor-pointer' : 'opacity-50'}`} title={t.description}>
                      <input
                        type="checkbox"
                        className="mt-0.5 accent-emerald-500"
                        disabled={!valid}
                        checked={declared.includes(t.name)}
                        onChange={(e) => set('tools')(toggleSystemTool(form.tools, t.name, e.target.checked))}
                      />
                      <span className="font-mono text-gray-300 flex items-center gap-1">
                        <Lock className="w-3 h-3 text-gray-500" />
                        {t.name}
                      </span>
                    </label>
                  ))}
                  {!valid && <p className="col-span-2 text-[11px] text-gray-500">Fix the tools JSON to change the system tools.</p>}
                </div>
              );
            })()}
          </div>
        )}

        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider">Tools (OpenAI format)</label>
            <button
              type="button"
              onClick={() => setShowGenerator(!showGenerator)}
              className="flex items-center gap-1 text-xs text-emerald-400 hover:text-emerald-300"
            >
              <Sparkles className="w-3.5 h-3.5" />
              Generate tool
            </button>
          </div>
          {showGenerator && (
            <div className="mb-3 space-y-2">
              <ToolGenerator
                model={form.model}
                onGenerated={(tool) => {
                  setGeneratedTool(JSON.stringify(toAssistantTool(tool.name, tool.description ?? '', tool.parameters ?? {}), null, 2));
                  setGeneratorError(null);
                }}
              />
              {generatedTool && (
                <>
                  <JsonEditor value={generatedTool} onChange={setGeneratedTool} height="h-48" />
                  <div className="flex items-center justify-end">
                    <button
                      type="button"
                      onClick={addGeneratedTool}
                      className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded transition flex items-center gap-1"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      Add
                    </button>
                  </div>
                </>
              )}
              {generatorError && <p className="text-xs text-red-400">{generatorError}</p>}
            </div>
          )}
          <JsonEditor value={form.tools} onChange={set('tools')} height="h-56" />
          {fieldError('tools')}
        </div>

        <div>
          <Label>Metadata</Label>
          <JsonEditor value={form.metadata} onChange={set('metadata')} height="h-24" />
          {fieldError('metadata')}
        </div>

        {assistant && (
          <div>
            <button onClick={() => setShowRaw(!showRaw)} className="flex items-center gap-1 text-xs text-gray-400 hover:text-white">
              {showRaw ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
              Full JSON (as saved)
            </button>
            {showRaw && (
              <div className="mt-2">
                <JsonEditor value={JSON.stringify(assistant, null, 2)} onChange={() => {}} readOnly height="h-64" />
              </div>
            )}
          </div>
        )}

        <div className="flex items-center justify-end space-x-3 pt-4 border-t border-[#2d2d2d]">
          <button onClick={onClose} className="px-4 py-2 text-sm text-gray-400 hover:text-white transition">
            Cancel
          </button>
          <button
            onClick={save}
            disabled={isSaving || !form.model}
            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-sm font-bold rounded shadow-lg transition flex items-center space-x-2"
          >
            <Save className="w-4 h-4" />
            <span>{isSaving ? 'Saving...' : 'Save'}</span>
          </button>
        </div>
      </div>
    </Modal>
  );
};

export default AssistantEditor;
