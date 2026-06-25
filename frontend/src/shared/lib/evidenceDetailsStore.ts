import { create } from "zustand";

import { readUiPreference, writeUiPreference } from "./browserStorage";

interface EvidenceDetailsState {
  enabled: boolean;
  setEnabled: (enabled: boolean) => void;
}

const STORAGE_KEY = "medkernel.evidence-details.enabled";

function readInitial() {
  if (typeof window === "undefined") return false;
  return readUiPreference(STORAGE_KEY) === "true";
}

export const useEvidenceDetailsStore = create<EvidenceDetailsState>((set) => ({
  enabled: readInitial(),
  setEnabled: (enabled) => {
    writeUiPreference(STORAGE_KEY, enabled ? "true" : "false");
    set({ enabled });
  },
}));
