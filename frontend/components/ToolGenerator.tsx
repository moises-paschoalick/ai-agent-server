import React, { useState } from 'react';
import { Sparkles } from 'lucide-react';
import { apiService } from '../services/api';

export interface GeneratedTool {
  name: string;
  description: string;
  parameters: any;
}

interface ToolGeneratorProps {
  /** Model of the call; empty uses the global model of the chat. */
  model?: string | null;
  onGenerated: (tool: GeneratedTool) => void;
}

/**
 * "What should the tool do?" plus Generate: the model suggests name, description and parameters
 * (POST /tools/generate), nothing is saved. Errors stay here, with what was typed.
 */
const ToolGenerator: React.FC<ToolGeneratorProps> = ({ model, onGenerated }) => {
  const [description, setDescription] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const generate = async () => {
    setIsGenerating(true);
    setError(null);
    try {
      onGenerated(await apiService.generateTool(description.trim(), model));
    } catch (e: any) {
      setError(e?.message ?? 'Could not generate the tool.');
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <div className="space-y-2 rounded-md border border-[#2d2d2d] bg-[#1a1a1a] p-3">
      <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider">What should the tool do?</label>
      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        maxLength={2000}
        rows={2}
        placeholder="e.g. looks up the status of an order by its number"
        className="w-full bg-[#212121] border border-[#2d2d2d] rounded-md px-3 py-2 focus:ring-1 focus:ring-emerald-500 outline-none text-sm resize-none"
      />
      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] text-gray-500 truncate">Model: {model || 'global'}</span>
        <button
          type="button"
          onClick={generate}
          disabled={isGenerating || !description.trim()}
          className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-bold rounded transition flex items-center gap-1"
        >
          <Sparkles className="w-3.5 h-3.5" />
          {isGenerating ? 'Generating…' : 'Generate'}
        </button>
      </div>
      {error && <p className="text-xs text-red-400">{error}</p>}
    </div>
  );
};

export default ToolGenerator;
