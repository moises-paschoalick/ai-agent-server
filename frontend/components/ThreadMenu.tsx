import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, History, MessageSquare, Plus, Search, Trash2 } from 'lucide-react';
import { AssistantSummary, Thread } from '../types';
import { groupThreads, shortId, timeAgo } from '../services/mappers';

interface ThreadMenuProps {
  threads: Thread[];
  assistants: AssistantSummary[];
  selectedAssistantId: string | null;
  currentThreadId: string | null;
  onOpen: (thread: Thread) => void;
  onNew: () => void;
  onDelete: (threadId: string) => void;
  onViewHistory: (threadId: string) => void;
  onRefresh: () => void;
}

/**
 * Every thread, grouped by assistant (the selected one first, "no assistant" last), with search.
 * Lives in the chat header; closes on Esc and on a click outside.
 */
const ThreadMenu: React.FC<ThreadMenuProps> = ({
  threads,
  assistants,
  selectedAssistantId,
  currentThreadId,
  onOpen,
  onNew,
  onDelete,
  onViewHistory,
  onRefresh,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    onRefresh();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setIsOpen(false);
    const onClick = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setIsOpen(false);
    };
    document.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onClick);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('mousedown', onClick);
    };
  }, [isOpen]); // onRefresh changes identity on every render of the parent

  const groups = useMemo(
    () => groupThreads(threads, assistants, selectedAssistantId, query),
    [threads, assistants, selectedAssistantId, query],
  );

  const current = threads.find((t) => t.conversation_id === currentThreadId);
  const assistantName = (id: string | null) =>
    id === null ? null : assistants.find((a) => a.id === id)?.name ?? id;
  const selectedName = assistantName(selectedAssistantId);
  const label = currentThreadId
    ? `${current ? (assistantName(current.assistantId) ?? 'Sem assistant') + ' · ' : ''}${current?.name ?? shortId(currentThreadId)}`
    : 'Nova conversa';

  return (
    <div ref={rootRef} className="relative min-w-0">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center space-x-1.5 max-w-md min-w-0 px-2 py-1 rounded hover:bg-[#212121] transition"
        title={currentThreadId ?? 'Open the thread menu'}
      >
        <span className="text-sm font-medium truncate">{label}</span>
        <ChevronDown className={`w-4 h-4 text-gray-400 shrink-0 transition ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {isOpen && (
        <div className="absolute left-0 top-full mt-2 w-[26rem] max-w-[calc(100vw-2rem)] max-h-[70vh] flex flex-col bg-[#171717] border border-[#2d2d2d] rounded-lg shadow-2xl z-30">
          <div className="p-3 border-b border-[#2d2d2d] space-y-2">
            <div className="flex items-center gap-2 bg-[#212121] border border-[#2d2d2d] rounded-md px-2">
              <Search className="w-4 h-4 text-gray-500 shrink-0" />
              <input
                autoFocus
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Buscar thread (título ou id)"
                className="flex-1 bg-transparent py-1.5 text-sm outline-none"
              />
            </div>
            <button
              onClick={() => {
                onNew();
                setIsOpen(false);
              }}
              className="w-full flex items-center space-x-2 text-sm text-emerald-500 hover:text-emerald-400 px-1 py-1"
            >
              <Plus className="w-4 h-4" />
              <span className="truncate">Nova thread ({selectedName ?? 'sem assistant'})</span>
            </button>
          </div>

          <div className="overflow-y-auto p-2 space-y-3">
            {groups.length === 0 && (
              <p className="text-xs text-gray-500 italic px-2 py-1">{query ? 'Nenhuma thread encontrada.' : 'Nenhuma thread ainda.'}</p>
            )}
            {groups.map((group) => (
              <div key={group.assistantId ?? '-'}>
                <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-gray-500 truncate">
                  {group.label} <span className="font-normal normal-case">({group.threads.length})</span>
                </p>
                {group.threads.map((t) => (
                  <div key={t.conversation_id} className="group relative flex items-center">
                    <button
                      onClick={() => {
                        onOpen(t);
                        setIsOpen(false);
                      }}
                      className={`w-full text-left px-2 py-1.5 rounded-md flex items-center space-x-2 text-sm transition ${
                        t.conversation_id === currentThreadId ? 'bg-[#2d2d2d] text-white' : 'text-gray-300 hover:bg-[#212121]'
                      }`}
                    >
                      <MessageSquare className="w-3.5 h-3.5 text-gray-500 shrink-0" />
                      <span className="truncate flex-1">{t.name}</span>
                      <span className="text-[10px] text-gray-500 shrink-0 group-hover:invisible">{timeAgo(t.lastActivityAt)}</span>
                    </button>
                    <div className="absolute right-1 hidden group-hover:flex items-center space-x-1 bg-[#212121] pl-1">
                      <button
                        onClick={() => {
                          onViewHistory(t.conversation_id);
                          setIsOpen(false);
                        }}
                        className="p-1 text-gray-500 hover:text-blue-400"
                        title="View History"
                      >
                        <History className="w-3.5 h-3.5" />
                      </button>
                      <button onClick={() => onDelete(t.conversation_id)} className="p-1 text-gray-500 hover:text-red-400" title="Delete Thread">
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default ThreadMenu;
