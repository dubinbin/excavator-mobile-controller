export const StepBar = ({
  length,
  activeStep,
}: {
  length: number;
  activeStep: number;
}) => {
  const stepCount = Math.max(0, Math.floor(length));
  const currentStep = Math.min(Math.max(activeStep, 0), stepCount - 1);

  return (
    <div
      className="fixed bottom-10 left-0 right-0 grid w-full gap-6 px-10"
      style={{
        gridTemplateColumns: `repeat(${stepCount}, minmax(0, 1fr))`,
      }}
    >
      {Array.from({ length: stepCount }).map((_, index) => (
        <div
          key={index}
          className={[
            "h-3 rounded-full",
            index === currentStep ? "bg-[#AFCBFF]" : "bg-[#ECEEF4]",
          ].join(" ")}
        />
      ))}
    </div>
  );
};
