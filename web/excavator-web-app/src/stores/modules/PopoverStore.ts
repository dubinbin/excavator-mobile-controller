import { type ReactNode } from "react";
import { create } from "zustand";

interface PopoverState {
  content: ReactNode | null;
  anchorRect: DOMRect | null;
  triggerRect: DOMRect | null;
  setTrigger: (anchor: HTMLElement | DOMRect) => void;
  show: (content: ReactNode, anchor?: HTMLElement | DOMRect) => void;
  hide: () => void;
}

const getAnchorRect = (anchor?: HTMLElement | DOMRect) => {
  if (!anchor) {
    return null;
  }

  if (anchor instanceof DOMRect) {
    return anchor;
  }

  return anchor.getBoundingClientRect();
};

export const usePopoverStore = create<PopoverState>((set) => ({
  content: null,
  anchorRect: null,
  triggerRect: null,
  setTrigger: (anchor) => {
    set({
      triggerRect: getAnchorRect(anchor),
    });
  },
  show: (content, anchor) => {
    set((state) => ({
      content,
      anchorRect: getAnchorRect(anchor) ?? state.triggerRect,
    }));
  },
  hide: () => {
    set({
      content: null,
      anchorRect: null,
    });
  },
}));

export const Popover = {
  setTrigger(anchor: HTMLElement | DOMRect) {
    usePopoverStore.getState().setTrigger(anchor);
  },
  show(content: ReactNode, anchor?: HTMLElement | DOMRect) {
    usePopoverStore.getState().show(content, anchor);
  },
  hide() {
    usePopoverStore.getState().hide();
  },
};
