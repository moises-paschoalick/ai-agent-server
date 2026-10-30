import React from 'react';
import { X } from 'lucide-react';

interface HistoryViewProps {
    threadId: string;
    history: any[];
    onClose: () => void;
}

const HistoryView: React.FC<HistoryViewProps> = ({ threadId, history, onClose }) => {
    return (
        <div className="w-96 bg-[#171717] border-l border-[#2d2d2d] flex flex-col h-full absolute right-0 top-0 z-20 shadow-xl">
            <div className="p-4 border-b border-[#2d2d2d] flex items-center justify-between bg-[#171717]">
                <div>
                    <h2 className="text-lg font-bold text-white">Thread History</h2>
                    <p className="text-xs text-gray-500 font-mono mt-1">{threadId}</p>
                </div>
                <button onClick={onClose} className="text-gray-400 hover:text-white">
                    <X className="w-5 h-5" />
                </button>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {history.map((msg, idx) => (
                    <div key={idx} className="bg-[#212121] rounded p-3 border border-[#2d2d2d]">
                        <div className="flex justify-between items-center mb-2">
                            <span className={`text-xs font-bold uppercase ${msg.role === 'user' ? 'text-blue-400' :
                                msg.role === 'assistant' ? 'text-emerald-400' :
                                    msg.role === 'tool' ? 'text-purple-400' : 'text-gray-400'
                                }`}>
                                {msg.role}
                            </span>
                            <span className="text-[10px] text-gray-600">
                                {msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString() : ''}
                            </span>
                        </div>

                        <div className="text-sm text-gray-300 whitespace-pre-wrap font-mono break-words">
                            {msg.content || (msg.tool_call_id ? `Tool Call: ${msg.name}` : '')}
                        </div>

                        {msg.tool_call_id && (
                            <div className="mt-2 text-[10px] text-gray-500 font-mono bg-black/20 p-1 rounded">
                                ID: {msg.tool_call_id}
                            </div>
                        )}

                        {msg.metadata && (
                            <div className="mt-2 grid grid-cols-2 gap-2 text-[10px] text-gray-400 font-mono bg-[#1a1a1a] p-2 rounded border border-[#2d2d2d]">
                                <div className="flex justify-between">
                                    <span>Latency:</span>
                                    <span className="text-emerald-500">{msg.metadata.latency_ms}ms</span>
                                </div>
                                <div className="flex justify-between">
                                    <span>Model:</span>
                                    <span className="text-purple-400">{msg.metadata.model || 'Unknown'}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span>Tokens:</span>
                                    <span className="text-blue-400">{msg.metadata.total_tokens}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span>Prompt:</span>
                                    <span>{msg.metadata.prompt_tokens}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span>Completion:</span>
                                    <span>{msg.metadata.completion_tokens}</span>
                                </div>
                                {msg.metadata.total_cost > 0 && (
                                    <div className="col-span-2 flex justify-between border-t border-[#2d2d2d] pt-1 mt-1">
                                        <span>Cost:</span>
                                        <span className="text-yellow-500">${msg.metadata.total_cost.toFixed(6)}</span>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                ))}
            </div>
        </div>
    );
};

export default HistoryView;
