
import React, { useState } from 'react';
import { Message, MessageRole } from '../types';
import { User, Bot, Wrench, ChevronRight, Copy, Check } from 'lucide-react';

interface MessageItemProps {
  message: Message;
  onToolSubmit: (toolName: string, output: string, toolCallId?: string) => void;
  isDebugMode: boolean;
}

const MessageItem: React.FC<MessageItemProps> = ({ message, onToolSubmit, isDebugMode }) => {
  const isUser = message.role === MessageRole.USER;
  const isTool = message.role === MessageRole.TOOL_CALL || message.role === MessageRole.TOOL_RESULT;
  const [toolOutput, setToolOutput] = useState('');
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(JSON.stringify(message, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={`flex flex-col space-y-2 animate-in fade-in slide-in-from-bottom-2 duration-300 ${isUser ? 'items-end' : 'items-start'}`}>
      <div className={`flex space-x-3 max-w-[85%] ${isUser ? 'flex-row-reverse space-x-reverse' : 'flex-row'}`}>
        {/* Avatar */}
        <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 shadow-inner ${
          isUser ? 'bg-blue-600' : 
          isTool ? 'bg-orange-600' : 'bg-emerald-600'
        }`}>
          {isUser ? <User className="w-5 h-5 text-white" /> : 
           isTool ? <Wrench className="w-5 h-5 text-white" /> : <Bot className="w-5 h-5 text-white" />}
        </div>

        {/* Bubble */}
        <div className={`relative group flex flex-col ${isUser ? 'items-end' : 'items-start'}`}>
          <div className={`px-4 py-3 rounded-2xl shadow-lg border ${
            isUser ? 'bg-blue-600/10 border-blue-500/30 text-blue-50' : 
            message.role === MessageRole.TOOL_CALL ? 'bg-orange-600/10 border-orange-500/30 text-orange-50' :
            message.role === MessageRole.TOOL_RESULT ? 'bg-orange-900/10 border-orange-800/20 text-orange-200 opacity-80' :
            'bg-[#171717] border-[#2d2d2d] text-gray-200'
          }`}>
            {/* Header for tools */}
            {(message.role === MessageRole.TOOL_CALL || message.role === MessageRole.TOOL_RESULT) && (
              <div className="flex items-center space-x-2 mb-2 pb-2 border-b border-orange-500/20">
                <span className="text-[10px] font-bold uppercase tracking-widest text-orange-500">
                  {message.role === MessageRole.TOOL_CALL ? 'TOOL CALL' : 'TOOL RESULT'}
                </span>
                <span className="text-xs font-mono font-bold text-orange-400">
                  {message.toolDetails?.toolName}
                </span>
              </div>
            )}

            {/* Content (with a cursor while the answer is still arriving) */}
            <div className="text-sm leading-relaxed whitespace-pre-wrap">
              {message.content}
              {message.streaming && (
                <span className="inline-block w-2 h-4 ml-0.5 -mb-0.5 bg-emerald-500 animate-pulse" aria-label="writing" />
              )}
            </div>
            {message.error && (
              <p className="mt-2 text-xs text-red-400">{message.error}</p>
            )}

            {/* Tool Inputs display */}
            {message.role === MessageRole.TOOL_CALL && message.toolDetails?.toolInput && (
              <div className="mt-2 p-2 bg-black/40 rounded border border-orange-500/10 font-mono text-[11px] overflow-x-auto">
                <div className="text-orange-500/60 mb-1 uppercase font-bold text-[9px]">Input Parameters:</div>
                {JSON.stringify(message.toolDetails.toolInput, null, 2)}
              </div>
            )}

            {/* Tool Interaction */}
            {message.role === MessageRole.TOOL_CALL && (
              <div className="mt-4 pt-4 border-t border-orange-500/20 space-y-3">
                <p className="text-xs text-orange-500 font-medium italic">Aguardando resultado da tool...</p>
                <div className="flex flex-col space-y-2">
                  <textarea 
                    value={toolOutput}
                    onChange={(e) => setToolOutput(e.target.value)}
                    placeholder="Enter tool result..."
                    className="w-full bg-black/50 border border-orange-500/30 rounded p-2 text-xs font-mono focus:ring-1 focus:ring-orange-500 outline-none text-orange-100 placeholder-orange-900"
                    rows={2}
                  />
                  <button 
                    onClick={() => onToolSubmit(message.toolDetails?.toolName || '', toolOutput, message.toolDetails?.toolCallId)}
                    className="self-end px-3 py-1.5 bg-orange-600 hover:bg-orange-500 text-white rounded text-xs font-bold transition flex items-center space-x-1"
                  >
                    <span>Submit Result</span>
                    <ChevronRight className="w-3 h-3" />
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Metadata / Time */}
          <div className="mt-1 flex items-center space-x-3">
            <span className="text-[10px] text-gray-500 font-medium">
              {new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </span>
            {isDebugMode && message.metadata?.latency_ms !== undefined && (
              <span className="text-[10px] text-gray-500 font-mono">
                ttft {message.metadata.ttft_ms ?? '–'} ms · total {message.metadata.latency_ms} ms
              </span>
            )}
            {isDebugMode && (
              <button 
                onClick={handleCopy}
                className="opacity-0 group-hover:opacity-100 transition flex items-center space-x-1 text-[10px] text-emerald-500 hover:text-emerald-400"
              >
                {copied ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
                <span>{copied ? 'Copied' : 'Debug JSON'}</span>
              </button>
            )}
          </div>
        </div>
      </div>
      
      {/* Debug view */}
      {isDebugMode && (
        <div className="w-full mt-2 p-2 bg-[#050505] border border-emerald-500/20 rounded-lg overflow-x-auto">
          <pre className="text-[10px] text-emerald-500/60 font-mono">
            {JSON.stringify(message, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
};

export default MessageItem;
