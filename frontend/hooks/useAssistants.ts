import { useCallback, useEffect, useState } from 'react';
import { AssistantSummary } from '../types';
import { ApiError, apiService } from '../services/api';
import { toAssistantSummary } from '../services/mappers';

const SELECTED_KEY = 'selected_assistant_id';

const readSelected = () => {
  try {
    return localStorage.getItem(SELECTED_KEY);
  } catch {
    return null;
  }
};

const writeSelected = (id: string | null) => {
  try {
    if (id) localStorage.setItem(SELECTED_KEY, id);
    else localStorage.removeItem(SELECTED_KEY);
  } catch {
    // only a convenience
  }
};

const errorText = (err: unknown, fallback: string) => (err instanceof Error && err.message ? err.message : fallback);

/**
 * The /v1 assistants and the selected one (null = "no assistant", the global chat settings).
 * Selecting writes nothing to the backend: the chat follows the assistant of each thread.
 */
export function useAssistants() {
  const [assistants, setAssistants] = useState<AssistantSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(readSelected);
  const [assistant, setAssistant] = useState<any | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const select = useCallback((id: string | null) => {
    setSelectedId(id);
    writeSelected(id);
  }, []);

  /** Reloads the list; the selection is kept (or set to `keep`) when it still exists. */
  const reload = useCallback(async (keep?: string | null) => {
    setIsLoading(true);
    try {
      const list = (await apiService.listAssistants()).map(toAssistantSummary);
      setAssistants(list);
      setError(null);
      setSelectedId((current) => {
        const wanted = keep !== undefined ? keep : current;
        const id = wanted && list.some((a) => a.id === wanted) ? wanted : null;
        writeSelected(id);
        return id;
      });
    } catch (err) {
      setError(errorText(err, 'Failed to load assistants.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  // Full object of the selected assistant, for the sidebar fields
  useEffect(() => {
    if (!selectedId) {
      setAssistant(null);
      return;
    }
    let cancelled = false;
    apiService
      .getAssistant(selectedId)
      .then((a) => !cancelled && setAssistant(a))
      .catch((err) => {
        if (cancelled) return;
        setAssistant(null);
        setError(errorText(err, 'Failed to load the assistant.'));
        if (err instanceof ApiError && err.status === 404) reload(null);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedId, reload]);

  /** Partial update of the selected assistant (POST /v1/assistants/{id}); throws on error. */
  const update = useCallback(
    async (patch: Record<string, unknown>) => {
      if (!selectedId) return;
      const saved = await apiService.updateAssistant(selectedId, patch);
      setAssistant(saved);
      await reload(saved.id);
    },
    [selectedId, reload],
  );

  /** After the editor saved: select it and refresh its detail. */
  const saved = useCallback(
    async (a: any) => {
      await reload(a.id);
      setAssistant(a);
    },
    [reload],
  );

  return { assistants, selectedId, assistant, isLoading, error, setError, select, reload, update, saved };
}
