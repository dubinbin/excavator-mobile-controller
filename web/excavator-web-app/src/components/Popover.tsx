import { usePopoverStore } from "../stores/modules/PopoverStore";

export const PopoverHost = () => {
  const { content, anchorRect, hide } = usePopoverStore();

  if (!content) {
    return null;
  }

  const top = anchorRect ? anchorRect.bottom + 12 : 88;
  const right = anchorRect ? window.innerWidth - anchorRect.right : 24;

  return (
    <div className="fixed inset-0 z-50 text-left" onClick={hide}>
      <div
        className="absolute w-50 w-[min(520px,calc(100vw-48px))] rounded-xl border border-[#E5E7EB] bg-white p-7 pr-14 text-[#111] shadow-[0_16px_50px_rgba(22,42,90,0.16)]"
        style={{ top, right }}
        id="popover_common"
        onClick={(event) => {
          event.stopPropagation();
        }}
      >
        <button
          className="absolute right-4 top-4 flex size-12 items-center justify-center rounded-full text-5xl leading-none text-[#333] hover:bg-black/5"
          type="button"
          aria-label="关闭"
          onClick={hide}
        >
          ×
        </button>

        {content}
      </div>
    </div>
  );
};
