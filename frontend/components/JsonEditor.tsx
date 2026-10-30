import React, { useState, useEffect } from 'react';
import { Settings, AlertCircle, Check } from 'lucide-react';

interface JsonEditorProps {
    value: string;
    onChange: (value: string) => void;
    height?: string;
    readOnly?: boolean;
}

const JsonEditor: React.FC<JsonEditorProps> = ({ value, onChange, height = 'h-40', readOnly = false }) => {
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        validateJson(value);
    }, [value]);

    const validateJson = (json: string) => {
        try {
            JSON.parse(json);
            setError(null);
            return true;
        } catch (e: any) {
            setError(e.message);
            return false;
        }
    };

    const handleFormat = () => {
        try {
            const parsed = JSON.parse(value);
            const formatted = JSON.stringify(parsed, null, 2);
            onChange(formatted);
            setError(null);
        } catch (e) {
            // Keep error state
        }
    };

    return (
        <div className="flex flex-col space-y-1">
            <div className="flex justify-between items-center bg-[#212121] px-2 py-1 rounded-t-md border border-[#2d2d2d] border-b-0">
                <span className="text-[10px] text-gray-400 uppercase font-bold flex items-center gap-1">
                    JSON
                    {error ? <AlertCircle className="w-3 h-3 text-red-500" /> : <Check className="w-3 h-3 text-emerald-500" />}
                </span>
                <button
                    onClick={handleFormat}
                    disabled={!!error || readOnly}
                    className={`text-[10px] px-2 py-0.5 rounded ${error || readOnly ? 'text-gray-600 cursor-not-allowed' : 'text-emerald-500 hover:bg-emerald-500/10'}`}
                >
                    Format
                </button>
            </div>
            <div className="relative">
                <textarea
                    value={value}
                    onChange={(e) => !readOnly && onChange(e.target.value)}
                    className={`w-full bg-black/30 text-[11px] font-mono p-2 rounded-b-md border outline-none resize-y ${height} ${error ? 'border-red-500/50' : 'border-[#2d2d2d]'} ${readOnly ? 'cursor-default text-gray-400' : ''}`}
                    spellCheck={false}
                    readOnly={readOnly}
                />
            </div>
            {error && (
                <div className="text-[10px] text-red-400 px-1 truncate" title={error}>
                    Error: {error}
                </div>
            )}
        </div>
    );
};

export default JsonEditor;
