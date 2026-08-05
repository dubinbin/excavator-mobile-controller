import { type ReactNode } from "react";
import { useCommonToastStore } from "@/stores/modules/CommonToastStore";

const DEFAULT_DURATION = 2_000;

export const CommonToast = {
  success(message: ReactNode, duration = DEFAULT_DURATION) {
    useCommonToastStore.getState().show({
      type: "success",
      message,
      duration,
    });
  },
  error(message: ReactNode, duration = DEFAULT_DURATION) {
    useCommonToastStore.getState().show({
      type: "error",
      message,
      duration,
    });
  },
  hide() {
    useCommonToastStore.getState().hide();
  },
};
