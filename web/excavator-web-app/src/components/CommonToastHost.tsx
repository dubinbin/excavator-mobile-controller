import { useEffect } from "react";
import { useCommonToastStore } from "@/stores/modules/CommonToastStore";

const SuccessIcon = () => (
  <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-[#35C83A] text-[22px] font-semibold leading-none text-white">
    ✓
  </span>
);

const ErrorIcon = () => (
  <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-[#F04438] text-white">
    <svg
      className="size-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      aria-hidden="true"
    >
      <path d="m7 7 10 10M17 7 7 17" />
    </svg>
  </span>
);

export const CommonToastHost = () => {
  const { toast, hide } = useCommonToastStore();

  useEffect(() => {
    if (!toast) return;

    const timer = window.setTimeout(() => {
      const currentToast = useCommonToastStore.getState().toast;

      if (currentToast?.id === toast.id) {
        hide();
      }
    }, Math.max(0, toast.duration ?? 2_000));

    return () => window.clearTimeout(timer);
  }, [hide, toast]);

  if (!toast) return null;

  const isSuccess = toast.type === "success";

  return (
    <div className="pointer-events-none fixed inset-x-0 top-8 z-[80] flex justify-center px-8">
      <div
        key={toast.id}
        className={[
          "flex min-h-16 min-w-[360px] max-w-[760px] items-center gap-4 rounded-xl border bg-white px-6 py-4 text-left",
          "shadow-[0_14px_42px_rgba(15,23,42,0.18)]",
          isSuccess ? "border-[#ABEFC6]" : "border-[#FECDCA]",
        ].join(" ")}
        role={isSuccess ? "status" : "alert"}
        aria-live={isSuccess ? "polite" : "assertive"}
      >
        {isSuccess ? <SuccessIcon /> : <ErrorIcon />}
        <div className="text-[22px] font-medium leading-[1.4] text-[#344054]">
          {toast.message}
        </div>
      </div>
    </div>
  );
};
