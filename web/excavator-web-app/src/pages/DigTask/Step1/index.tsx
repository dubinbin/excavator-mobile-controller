
import { useNavigate } from "react-router-dom";
import squareTypeImage from "@/assets/dig/square_type.png";
import trapezoidType from "@/assets/dig/trapezoid_type.png";
import { StepBar } from "@/components/StepBar";
import { CommonHeader } from "@/components/CommonHeader";
import { Popover } from "@/stores/modules/PopoverStore";
import squareActiveTypeImage from "@/assets/dig/square_type_active.png";
import trapezoidActiveType from "@/assets/dig/trapezoid_type_active.png";
import { useDigTaskState } from "@/stores/modules/DigTaskStore";
import { DigSelectedType } from "@/types";


// ### 6.3 挖沟流程

// 1. 遥控器发送功能选择帧，`MsgID = 0x04`（功能选择 / 退出），`FeatureID = 0x02`（挖沟），`Action = 0x01`（进入），进入挖沟功能。
// 2. TCU 回应功能选择应答帧，`MsgID = 0x84`，遥控器以 `ActiveFeature = 0x02`（挖沟）作为进入挖沟功能成功的判据。
// 3. 遥控器在本地选择沟型为方形沟或梯形沟，此时不需要立即发送给 TCU。
// 4. 遥控器发送通用测点请求帧，`MsgID = 0x10`，测 A 点，`FeatureID = 0x02`（挖沟），`PointID = 0x01`（A 点）。
// 5. TCU 回传测点结果帧，`MsgID = 0x90`，返回 A 点坐标。
// 6. 遥控器发送通用测点请求帧，`MsgID = 0x10`，测 B 点，`FeatureID = 0x02`（挖沟），`PointID = 0x02`（B 点）。
// 7. TCU 回传测点结果帧，`MsgID = 0x90`，返回 B 点坐标。
// 8. 遥控器在本地根据测得的 A/B 点和用户设置的填挖量，换算出实际用于建模的 A' / B' 点坐标，并完成沟型、沟深、宽度参数配置。
// 9. 用户最终确认后，遥控器发送挖沟参数整包下发帧，`MsgID = 0x20`，其中包含 A' / B' 点经纬度、高度以及沟深、宽度参数。
// 10. TCU 回传挖沟参数应答帧，`MsgID = 0xA0`，确认整包参数已接受。
// 11. 遥控器发送任务确认帧，`MsgID = 0x40`，`FeatureID = 0x02`（挖沟），`Action = 0x01`（确认任务开始生效）。
// 12. TCU 回传任务确认应答帧，`MsgID = 0xC0`，挖沟任务进入激活状态。



const digTypeOptions = [
  {
    id: DigSelectedType.square,
    title: "方形沟",
    image: squareTypeImage,
    activeImage: squareActiveTypeImage,
    imageClassName: "w-[76%]",
  },
  {
    id: DigSelectedType.trapezoid,
    title: "梯形沟",
    image: trapezoidType,
    activeImage: trapezoidActiveType,
    imageClassName: "w-[78%]",
  },
] as const;

export const DigTaskStep1 = () => {
  const navigation = useNavigate();
  const { digSelectedType, setDigSelectedType } = useDigTaskState();

  const handleDirectionSelect = (directionId: (typeof digTypeOptions)[number]["id"]) => {
    setDigSelectedType(directionId);
    setTimeout(() => {
        navigation("../step2");
    }, 200);
  }


  return (
    <div className="min-h-screen bg-[#F6F8FD] text-[#111] px-11 py-10">
      <CommonHeader
        title="挖沟作业设置"
        clickHelpAction={() => {
          Popover.show(
            <div className="text-2xl leading-relaxed flex flex-col gap-y-4 max-h-[60vh] overflow-auto">
              <p className="font-semibold">挖沟作业设置说明</p>
              <p>这里可以放你需要展示的帮助文本或自定义 JSX 内容。</p>
            </div>,
          );
        }}
      />

      <main className="rounded-2xl mt-7  bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-12">
        <p className="m-0 font-semibold leading-none text-black text-[32px]">
          Step 1 选择挖沟沟型
        </p>

        <div className="grid gap-8 mt-16 grid-cols-2">
          {digTypeOptions.map((option) => {
            const isSelected = digSelectedType === option.id;

            return (
              <button
                key={option.id}
                className={[
                  "flex aspect-838/760 min-h-90 flex-col items-center rounded-[20px] border px-8 pt-14 transition",
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
                    src={isSelected ? option.activeImage : option.image }
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
