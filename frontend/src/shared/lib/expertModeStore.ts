import { create } from "zustand";

import { readUiPreference, writeUiPreference } from "./browserStorage";

interface ExpertModeState {
  enabled: boolean;
  setEnabled: (enabled: boolean) => void;
}

const STORAGE_KEY = "medkernel.expert-mode.enabled";

function readInitial() {
  if (typeof window === "undefined") return false;
  return readUiPreference(STORAGE_KEY) === "true";
}

export const useExpertModeStore = create<ExpertModeState>((set) => ({
  enabled: readInitial(),
  setEnabled: (enabled) => {
    writeUiPreference(STORAGE_KEY, enabled ? "true" : "false");
    set({ enabled });
  },
}));
