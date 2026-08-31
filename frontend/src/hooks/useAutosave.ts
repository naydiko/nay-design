// Stage 1 dirty-tracking / debounced-autosave state machine shared by the
// level canvas and room furniture editors. Deliberately simple: no
// versioning, no operational-transform/CRDT machinery, no server-side
// concept of "drafts" — just a client-side debounce plus a monotonically
// increasing "generation" counter so a slow/older save response can never
// clobber edits made after it was sent.
import { useCallback, useEffect, useRef, useState } from "react";

export type SaveState = "saved" | "unsaved" | "saving" | "error";

/** Human-readable label for each save state, per the Stage 1 UI spec. */
export const SAVE_STATE_LABELS: Record<SaveState, string> = {
  saved: "Saved",
  unsaved: "Unsaved changes",
  saving: "Saving…",
  error: "Save failed",
};

interface UseAutosaveOptions {
  /**
   * Persists the current (latest) state to the backend. Must read fresh
   * data at call time (e.g. via component state in a closure) rather than
   * capturing it early, since this may run well after it was scheduled.
   * Should throw on failure.
   */
  save: () => Promise<void>;
  /** How long editing must be idle before an autosave is attempted. */
  delayMs?: number;
  /** Set to false to disable automatic saving; the explicit Save button
   * (via {@link saveNow}) keeps working regardless. */
  enabled?: boolean;
}

/**
 * Tracks whether an editor has unsaved changes and drives a conservative
 * debounced autosave:
 * - {@link markDirty} must be called only from *finished* edits (a commit,
 *   a drag ending on pointer-up, an add/delete) — never from continuous
 *   pointer-move handlers — so dragging never generates requests.
 * - The debounce timer restarts on every {@link markDirty} call, so a save
 *   is only attempted once editing has been idle for `delayMs`.
 * - Saves never overlap: a new autosave attempt while one is in flight is a
 *   no-op; if edits arrived *during* a save, one more autosave is scheduled
 *   right after it finishes so they aren't silently dropped.
 * - A failed save surfaces as `"error"` ("Save failed") without retrying
 *   automatically (to avoid hammering a broken backend) — the next edit or
 *   an explicit Save click will try again.
 */
export function useAutosave({ save, delayMs = 2000, enabled = true }: UseAutosaveOptions) {
  const [saveState, setSaveState] = useState<SaveState>("saved");

  // Always call the latest `save` closure, even if a timer was scheduled
  // against an older render.
  const saveRef = useRef(save);
  useEffect(() => {
    saveRef.current = save;
  }, [save]);

  const enabledRef = useRef(enabled);
  useEffect(() => {
    enabledRef.current = enabled;
  }, [enabled]);

  const savingRef = useRef(false);
  // Bumped by every markDirty() call; lets a save detect whether newer
  // edits arrived while it was in flight (or since it started), so callers
  // can avoid overwriting local state with a now-stale server response.
  const generationRef = useRef(0);
  const timerRef = useRef<number | undefined>(undefined);
  // Indirection to let scheduleAutosave/runSave reference each other
  // without a circular declaration-order dependency.
  const runSaveRef = useRef<() => Promise<void>>(async () => {});

  const clearTimer = useCallback(() => {
    if (timerRef.current !== undefined) {
      window.clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
  }, []);

  const scheduleAutosave = useCallback(() => {
    if (!enabledRef.current) return;
    clearTimer();
    timerRef.current = window.setTimeout(() => {
      timerRef.current = undefined;
      void runSaveRef.current();
    }, delayMs);
  }, [clearTimer, delayMs]);

  const runSave = useCallback(async () => {
    if (savingRef.current) return; // never allow overlapping saves
    const generationAtStart = generationRef.current;
    savingRef.current = true;
    setSaveState("saving");
    try {
      await saveRef.current();
      savingRef.current = false;
      if (generationRef.current === generationAtStart) {
        setSaveState("saved");
      } else {
        // Newer edits happened while this save was in flight: still dirty.
        // Make sure they eventually get persisted too.
        setSaveState("unsaved");
        scheduleAutosave();
      }
    } catch {
      savingRef.current = false;
      setSaveState("error");
    }
  }, [scheduleAutosave]);

  useEffect(() => {
    runSaveRef.current = runSave;
  }, [runSave]);

  useEffect(() => clearTimer, [clearTimer]);

  /** Call from every finished edit (never from a continuous drag handler). */
  const markDirty = useCallback(() => {
    generationRef.current += 1;
    setSaveState((s) => (s === "saving" ? s : "unsaved"));
    scheduleAutosave();
  }, [scheduleAutosave]);

  /** Saves immediately (the explicit Save button); supersedes any pending
   * debounced autosave so they don't race each other. */
  const saveNow = useCallback(async () => {
    clearTimer();
    await runSave();
  }, [clearTimer, runSave]);

  // Warn before leaving the page/tab while there are unsaved changes.
  useEffect(() => {
    function handler(e: BeforeUnloadEvent) {
      if (saveState !== "saved") {
        e.preventDefault();
        e.returnValue = "";
      }
    }
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [saveState]);

  const getGeneration = useCallback(() => generationRef.current, []);
  /** True if any markDirty() happened after the given generation token
   * (i.e. after a save that captured it started) — use to avoid applying a
   * now-stale server response over newer local edits. */
  const isStale = useCallback((token: number) => generationRef.current !== token, []);

  return { saveState, markDirty, saveNow, getGeneration, isStale };
}

