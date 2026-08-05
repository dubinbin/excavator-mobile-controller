import { type ReactNode } from "react";
import { create } from "zustand";

export type CommonToastType = "success" | "error";

export interface CommonToastOptions {
  type: CommonToastType;
  message: ReactNode;
  duration?: number;
}

export interface CommonToastItem extends CommonToastOptions {
  id: number;
}

interface CommonToastState {
  toast: CommonToastItem | null;
  show: (options: CommonToastOptions) => void;
  hide: () => void;
}

let nextToastId = 0;

export const useCommonToastStore = create<CommonToastState>((set) => ({
  toast: null,
  show: (options) => {
    nextToastId += 1;
    set({
      toast: {
        ...options,
        id: nextToastId,
      },
    });
  },
  hide: () => {
    set({ toast: null });
  },
}));
