import React, { useState } from 'react';
import { Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react';
import AssistantEditor from './AssistantEditor';
import { AssistantSummary } from '../types';
import { ApiError, apiService } from '../services/api';

interface AssistantPickerProps {
  models: string[];
  assistants: AssistantSummary[];
  /** null = "no assistant": the chat uses the global prompt, model and tools. */
  selectedId: string | null;
  isLoading: boolean;
  error: string | null;
  onSelect: (id: string | null) => void;
  onReload: (keep?: string | null) => void;
  onSaved: (assistant: any) => void;
}

const errorText = (err: unknown, fallback: string) => (err instanceof Error && err.message ? err.message : fallback);

const iconButton =
  'p-2 rounded-md border border-[#2d2d2d] bg-[#212121] text-gray-400 hover:text-white hover:border-emerald-500/50 transition disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:text-gray-400 disabled:hover:border-[#2d2d2d]';

/**
 * /v1 assistants at the top of the sidebar: pick one, create, edit, delete. Picking writes nothing
 * to the backend; the chat follows the assistant of each thread.
 */
const AssistantPicker: React.FC<AssistantPickerProps> = ({
  models,
  assistants,
  selectedId,
  isLoading,
  error,
  onSelect,
  onReload,
  onSaved,
}) => {
  const [actionError, setActionError] = useState<string | null>(null);
  const [editor, setEditor] = useState<{ open: boolean; assistant: any | null }>({ open: false, assistant: null });

  const selected = assistants.find((a) => a.id === selectedId) ?? null;

  const openEdit = async () => {
    if (!selected) return;
    try {
      setEditor({ open: true, assistant: await apiService.getAssistant(selected.id) });
      setActionError(null);
    } catch (err) {
      setActionError(errorText(err, 'Failed to load the assistant.'));
      if (err instanceof ApiError && err.status === 404) onReload(null);
    }
  };

  const remove = async () => {
    if (!selected) return;
    if (!window.confirm(`Delete assistant "${selected.name ?? '(sem nome)'}" (${selected.id})?`)) return;
    try {
      await apiService.deleteAssistant(selected.id);
      setActionError(null);
    } catch (err) {
      setActionError(errorText(err, 'Failed to delete the assistant.'));
    }
    onReload(null);
  };

  const shownError = actionError ?? error;

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider">Assistant</label>
        <button onClick={() => onReload()} disabled={isLoading} className="text-gray-500 hover:text-white disabled:opacity-50" title="Reload assistants">
          <RefreshCw className={`w-3 h-3 ${isLoading ? 'animate-spin' : ''}`} />
        </button>
      </div>
      <div className="flex items-center gap-1.5">
        <select
          value={selectedId ?? ''}
          onChange={(e) => onSelect(e.target.value || null)}
          className="flex-1 min-w-0 bg-[#212121] border border-[#2d2d2d] rounded-md px-3 py-2 focus:ring-1 focus:ring-emerald-500 outline-none text-sm"
          title={selected ? `${selected.id} · ${selected.toolCount} tools` : 'Global prompt, model and tools'}
        >
          <option value="">Sem assistant (global)</option>
          {assistants.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name ?? '(sem nome)'} · {a.model}
            </option>
          ))}
        </select>
        <button onClick={() => setEditor({ open: true, assistant: null })} className={iconButton} title="Create assistant">
          <Plus className="w-4 h-4" />
        </button>
        <button onClick={openEdit} disabled={!selected} className={iconButton} title="Edit assistant">
          <Pencil className="w-4 h-4" />
        </button>
        <button onClick={remove} disabled={!selected} className={`${iconButton} hover:!text-red-400`} title="Delete assistant">
          <Trash2 className="w-4 h-4" />
        </button>
      </div>
      {selected && <p className="text-[10px] text-gray-600 font-mono mt-1 truncate">{selected.id}</p>}
      {shownError && <p className="text-[11px] text-red-400 mt-1">{shownError}</p>}

      <AssistantEditor
        isOpen={editor.open}
        assistant={editor.assistant}
        models={models}
        onClose={() => setEditor({ open: false, assistant: null })}
        onSaved={(saved) => {
          setEditor({ open: false, assistant: null });
          onSaved(saved);
        }}
      />
    </div>
  );
};

export default AssistantPicker;
