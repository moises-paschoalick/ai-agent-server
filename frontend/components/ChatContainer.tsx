
import React, { useState, useRef, useEffect } from 'react';
import { Message, MessageRole } from '../types';
import MessageItem from './MessageItem';
import { Send, Terminal, Loader2, PanelLeftOpen, Code } from 'lucide-react';

interface ChatContainerProps {
  messages: Message[];
  onSendMessage: (text: string) => void;
  onToolSubmit: (toolName: string, output: string, toolCallId?: string) => void;
  isLoading: boolean;
  currentThreadId: string | null;
  isDebugMode: boolean;
  onToggleDebug: () => void;
  /** The thread menu, shown in the header. */
  threadMenu: React.ReactNode;
  /** Set while the left panel is closed: shows the button that opens it. */
  onOpenSidebar?: () => void;
}

const ChatContainer: React.FC<ChatContainerProps> = ({ 
  messages, 
  onSendMessage, 
  onToolSubmit, 
  isLoading, 
  currentThreadId,
  isDebugMode,
  onToggleDebug,
  threadMenu,
  onOpenSidebar
}) => {
  const [inputValue, setInputValue] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isLoading]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputValue.trim() && !isLoading) {
      onSendMessage(inputValue);
      setInputValue('');
    }
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-[#0b0b0b]">
      {/* Header */}
      <div className="h-14 border-b border-[#2d2d2d] flex items-center px-6 justify-between bg-[#111]">
        <div className="flex items-center space-x-3 min-w-0">
          {onOpenSidebar && (
            <button onClick={onOpenSidebar} title="Open panel" className="p-1.5 -ml-2 rounded transition text-gray-400 hover:bg-[#2d2d2d] hover:text-white">
              <PanelLeftOpen className="w-4 h-4" />
            </button>
          )}
          <Terminal className="w-5 h-5 text-emerald-500 shrink-0" />
          {threadMenu}
        </div>
        <div className="flex items-center space-x-3 shrink-0">
          {isLoading && (
            <div className="flex items-center space-x-2 text-emerald-500 text-xs font-medium animate-pulse">
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              <span>Processando...</span>
            </div>
          )}
          <button
            onClick={onToggleDebug}
            title="Toggle Debug Mode"
            className={`p-1.5 rounded transition ${isDebugMode ? 'bg-emerald-500/20 text-emerald-400' : 'text-gray-400 hover:bg-[#2d2d2d]'}`}
          >
            <Code className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Messages */}
      <div 
        ref={scrollRef}
        className="flex-1 overflow-y-auto p-4 space-y-4 max-w-4xl mx-auto w-full"
      >
        {messages.length === 0 && !isLoading && (
          <div className="h-full flex flex-col items-center justify-center text-gray-600 space-y-4">
            <div className="w-16 h-16 rounded-full bg-[#171717] flex items-center justify-center border border-[#2d2d2d]">
              <MessageRoleIcon role={MessageRole.ASSISTANT} />
            </div>
            <p className="text-lg font-light italic">Envie uma mensagem para iniciar a conversa.</p>
          </div>
        )}
        
        {messages.map((msg) => (
          <MessageItem 
            key={msg.id} 
            message={msg} 
            onToolSubmit={onToolSubmit} 
            isDebugMode={isDebugMode}
          />
        ))}

        {isLoading && messages[messages.length - 1]?.role !== MessageRole.TOOL_CALL && !messages[messages.length - 1]?.streaming && (
          <div className="flex justify-start">
            <div className="bg-[#171717] border border-[#2d2d2d] p-4 rounded-xl shadow-lg flex items-center space-x-2">
              <div className="w-2 h-2 bg-emerald-500 rounded-full animate-bounce [animation-delay:-0.3s]"></div>
              <div className="w-2 h-2 bg-emerald-500 rounded-full animate-bounce [animation-delay:-0.15s]"></div>
              <div className="w-2 h-2 bg-emerald-500 rounded-full animate-bounce"></div>
            </div>
          </div>
        )}
      </div>

      {/* Input */}
      <div className="p-4 border-t border-[#2d2d2d] bg-[#111]">
        <form 
          onSubmit={handleSubmit}
          className="max-w-4xl mx-auto flex items-center bg-[#212121] border border-[#2d2d2d] rounded-xl focus-within:border-emerald-500/50 focus-within:ring-1 focus-within:ring-emerald-500/20 transition group"
        >
          <input 
            type="text" 
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            disabled={isLoading}
            placeholder={!currentThreadId ? "Escreva para começar uma nova thread..." : "Enter your message..."}
            className="flex-1 bg-transparent px-5 py-4 outline-none text-sm placeholder-gray-500 disabled:opacity-50"
          />
          <button 
            type="submit"
            disabled={!inputValue.trim() || isLoading}
            className="px-5 py-2 mr-2 bg-emerald-600 hover:bg-emerald-500 disabled:bg-[#2d2d2d] disabled:text-gray-600 rounded-lg text-white font-semibold flex items-center space-x-2 transition-all active:scale-95 shadow-lg"
          >
            <span>Run</span>
            <Send className="w-4 h-4" />
          </button>
        </form>
        <p className="mt-2 text-center text-[10px] text-gray-500">
          Assistant responses are generated by the model specified in config.
        </p>
      </div>
    </div>
  );
};

const MessageRoleIcon = ({ role }: { role: MessageRole }) => {
  switch (role) {
    case MessageRole.USER: return <div className="w-4 h-4 rounded-full bg-blue-500" />;
    case MessageRole.ASSISTANT: return <div className="w-4 h-4 rounded-full bg-emerald-500" />;
    default: return <Terminal className="w-4 h-4 text-orange-500" />;
  }
};

export default ChatContainer;
