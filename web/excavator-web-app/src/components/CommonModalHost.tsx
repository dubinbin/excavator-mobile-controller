import { useCommonModalStore } from "@/stores/modules/CommonModalStore";

export const CommonModalHost = () => {
  const { options, hide } = useCommonModalStore();

  if (!options) {
    return null;
  }

  const handleCancel = () => {
    options.cancelAction?.();
    hide();
  };

  const handleOk = () => {
    options.okAction?.();
    hide();
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/35 px-8 text-left">
      <div
        className="w-[560px] max-w-full rounded-2xl bg-white px-10 pb-9 pt-8 shadow-[0_22px_70px_rgba(15,23,42,0.22)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="common-modal-title"
      >
        <div id="common-modal-title" className="text-[30px] font-semibold leading-tight text-black">
          {options.title}
        </div>
        <div className="mt-5 text-[22px] leading-[1.5] text-[#667085]">{options.content}</div>

        <div className="mt-9 flex justify-end gap-4">
          <button
            className="h-14 min-w-32 rounded-xl border border-[#D7DDE8] bg-white px-8 text-[22px] font-medium text-[#344054] transition active:scale-[0.98]"
            type="button"
            onClick={handleCancel}
          >
            {options.cancelText ?? "取消"}
          </button>
          <button
            className="h-14 min-w-32 rounded-xl bg-[#0B63FF] px-8 text-[22px] font-medium text-white shadow-[0_10px_22px_rgba(0,91,255,0.24)] transition active:scale-[0.98]"
            type="button"
            onClick={handleOk}
          >
            {options.okText ?? "确定"}
          </button>
        </div>
      </div>
    </div>
  );
};

