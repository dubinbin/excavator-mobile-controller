import { type ReactNode } from "react";
import { create } from "zustand";

export interface CommonModalOptions {
  title: string;
  content: ReactNode;
  okText?: string;
  okAction?: () => void;
  cancelText?: string;
  cancelAction?: () => void;
}

interface CommonModalState {
  options: CommonModalOptions | null;
  show: (options: CommonModalOptions) => void;
  hide: () => void;
}

export const useCommonModalStore = create<CommonModalState>((set) => ({
  options: null,
  show: (options) => {
    set({ options });
  },
  hide: () => {
    set({ options: null });
  },
}));

