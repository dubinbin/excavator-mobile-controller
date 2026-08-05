import { useCommonModalStore, type CommonModalOptions } from "@/stores/modules/CommonModalStore";

export const CommonModal = {
  show(options: CommonModalOptions) {
    useCommonModalStore.getState().show(options);
  },
  hide() {
    useCommonModalStore.getState().hide();
  },
};

