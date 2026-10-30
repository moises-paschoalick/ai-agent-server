// Reader of server-sent events over fetch: EventSource cannot POST nor send a body.

export interface SseEvent {
  event: string;
  data: any;
}

/**
 * Parses one event block (`event:` and `data:` lines). `data` is JSON; several `data:` lines are
 * joined with a newline, as the SSE format says.
 */
export function parseEvent(block: string): SseEvent | null {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).replace(/^ /, ''));
    }
  }
  if (data.length === 0) {
    return null;
  }
  const text = data.join('\n');
  try {
    return { event, data: JSON.parse(text) };
  } catch {
    return { event, data: text };
  }
}

/**
 * Calls `onEvent` for every event of `body`, in order, as soon as each one is complete. Network
 * chunks may cut an event anywhere, or carry several: the text is kept until a blank line closes
 * the event.
 */
export async function readSse(body: ReadableStream<Uint8Array>, onEvent: (event: SseEvent) => void): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  const flush = () => {
    let end: number;
    while ((end = buffer.indexOf('\n\n')) >= 0) {
      const parsed = parseEvent(buffer.slice(0, end));
      buffer = buffer.slice(end + 2);
      if (parsed) {
        onEvent(parsed);
      }
    }
  };
  for (;;) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
    flush();
  }
  buffer += decoder.decode().replace(/\r\n/g, '\n');
  flush();
  if (buffer.trim()) {
    const parsed = parseEvent(buffer);
    if (parsed) {
      onEvent(parsed);
    }
  }
}
