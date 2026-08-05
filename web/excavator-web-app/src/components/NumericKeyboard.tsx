interface NumericKeyboardProps {
  value: string;
  onChange: (value: string) => void;
  onClear: () => void;
  onConfirm: () => void;
  
}

const keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "-"];

export const NumericKeyboard = ({
  value,
  onChange,
  onClear,
  onConfirm,
}: NumericKeyboardProps) => {
  const handleKeyPress = (key: string) => {
    if (key === "." && value.includes(".")) {
      return;
    }

    if (key === "-" && value.length > 0) {
      return;
    }

    onChange(`${value}${key}`);
  };

  return (
    <div className="rounded-2xl bg-[#F6F9FF] px-16 py-12 shadow-[0_12px_40px_rgba(24,68,145,0.08)] backdrop-blur">
      <div className="grid grid-cols-3 gap-x-10 gap-y-8">
        {keys.map((key) => (
          <button
            key={key}
            className="flex size-28 items-center justify-center rounded-full bg-white text-[48px] leading-none text-[#4A4A4A] shadow-[0_2px_10px_rgba(15,35,80,0.02)] active:bg-[#EEF4FF]"
            type="button"
            onClick={() => handleKeyPress(key)}
          >
            {key}
          </button>
        ))}
      </div>

      <div className="mt-11 grid grid-cols-[1fr_1.9fr] gap-6">
        <button
          className="h-24 rounded-xl border border-[#E7E7E7] bg-white text-[34px] leading-none text-black shadow-[0_5px_18px_rgba(15,35,80,0.04)] active:bg-[#F7F8FA]"
          type="button"
          onClick={onClear}
        >
          清除
        </button>
        <button
          className="h-24 rounded-xl bg-[#006BFF] text-[34px] font-semibold leading-none text-white shadow-[0_8px_20px_rgba(0,77,255,0.22)] active:bg-[#0058D6]"
          type="button"
          onClick={onConfirm}
        >
          确认
        </button>
      </div>
    </div>
  );
};
