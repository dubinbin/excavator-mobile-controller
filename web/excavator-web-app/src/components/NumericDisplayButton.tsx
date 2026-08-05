interface NumericDisplayButtonProps {
  value: string;
  isActive?: boolean;
  className?: string;
  valueClassName?: string;
  unit?: string;
  unitClassName?: string;
  onClick: () => void;
}

export const NumericDisplayButton = ({
  value,
  isActive = false,
  className = "",
  valueClassName = "",
  unit,
  unitClassName = "",
  onClick,
}: NumericDisplayButtonProps) => {
  return (
    <button
      className={[
        "text-left",
        isActive ? "border border-[#006BFF] ring-2 ring-[#006BFF]/10" : "border border-[#E3EBFF]",
        className,
      ].join(" ")}
      type="button"
      onClick={onClick}
    >
      <span className={valueClassName}>{value}</span>
      {unit ? <span className={unitClassName}>{unit}</span> : null}
    </button>
  );
};
