
import { useNavigate } from "react-router-dom";
import slopeTopImage from "@/assets/repaire-slope/step1_abpoint1.png";
import slopeBottomImage from "@/assets/repaire-slope/step1_abpoint2.png";
import { StepBar } from "@/components/StepBar";
import { CommonHeader } from "@/components/CommonHeader";
import slopeTopActiveImage from "@/assets/repaire-slope/step1_abpoint1_active.png";
import slopeBottomActiveImage from "@/assets/repaire-slope/step1_abpoint2_active.png";
import { useRepairSlopeStore } from "@/stores/modules/RepairSlopeStore";
import { SlopeSelectedType } from "@/types";

const directionOptions = [
  {
    id: SlopeSelectedType.top,
    title: "从坡顶开始修坡",
    image: slopeTopImage,
    activeImage: slopeTopActiveImage,
    imageClassName: "w-[76%]",
  },
  {
    id: SlopeSelectedType.bottom,
    title: "从坡底开始修坡",
    image: slopeBottomImage,
    activeImage: slopeBottomActiveImage,
    imageClassName: "w-[78%]",
  },
] as const;

export const RepairSlopeStep1 = () => {
  const navigation = useNavigate();
  const { repairSlopeSelectedType, setRepairSlopeSelectedType } = useRepairSlopeStore();

  const handleDirectionSelect = (directionId: (typeof directionOptions)[number]["id"]) => {
    setRepairSlopeSelectedType(directionId);
    setTimeout(() => {
        navigation("../step2");
    }, 200);
  }

  return (
    <div className="min-h-screen bg-[#F6F8FD] text-[#111] px-11 py-10">
     <CommonHeader title="直线修坡作业设置" />

      <main className="rounded-2xl mt-7 min-h-[82vh]  bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-12 ">
        <p className="m-0 font-semibold leading-none text-black text-[32px]">
          Step 1 选择修坡方向
        </p>

        <div className="grid mt-8 grid-cols-2 gap-12">
          {directionOptions.map((option) => {
            const isSelected = repairSlopeSelectedType === option.id;

            return (
              <button
                key={option.id}
                className={[
                  "flex aspect-[838/760] min-h-[360px] flex-col items-center rounded-[20px] border px-8 pt-14 transition",
                  "focus:outline-none focus:ring-4 focus:ring-[#006BFF]/20",
                  isSelected
                    ? "border-2 border-[#006BFF] bg-[#004DFF1A]"
                    : "border border-[#D4D4D4] bg-[#0C0C0C05]",
                ].join(" ")}
                type="button"
                onClick={() => {
                  handleDirectionSelect(option.id);
                }}
              >
                <div
                  className={[
                    "font-semibold leading-none text-[40px]",
                    isSelected ? "text-[#006BFF]" : "text-[#333333]",
                  ].join(" ")}
                >
                  {option.title}
                </div>

                <div className="flex min-h-0 flex-1 items-center justify-center pt-8">
                  <img
                    className={`${option.imageClassName} max-h-[82%] object-contain`}
                    src={ isSelected ? option.activeImage : option.image}
                    alt=""
                  />
                </div>
              </button>
            );
          })}
        </div>
      </main>


       <StepBar length={5} activeStep={0} />
   
    </div>
  );
};
