import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CommonHeader } from "@/components/CommonHeader";
import { NumericDisplayButton } from "@/components/NumericDisplayButton";
import { NumericKeyboard } from "@/components/NumericKeyboard";
import { StepBar } from "@/components/StepBar";
import bottomPointA from "@/assets/repaire-slope/step2_bottom_apoint.png";
import topPointA from "@/assets/repaire-slope/step2_top_apoint.png";
import { COMMON_DIRECTION, directionOptions, REPAIR_SLOPE_POINT_KEYS, SlopeSelectedType } from "@/types";
import { useRepairSlopeStore } from "@/stores/modules/RepairSlopeStore";
import { useMeasurementResult } from "@/components/useMeasurementResult";


const locationRows = [
    ["经度", REPAIR_SLOPE_POINT_KEYS.longitude],
    ["纬度", REPAIR_SLOPE_POINT_KEYS.latitude],
    ["高程", REPAIR_SLOPE_POINT_KEYS.height],
] as const;

export const RepairSlopeStep2 = () => {
  const navigation = useNavigate();
  const { repairSlopeSelectedType, PointAInfo, pointA_Mode, setPointAMode, setAPointInfo, setAPointBucketTeeth, selectedAPointBucketTeeth } = useRepairSlopeStore();

  const [manualValues, setManualValues] = useState<Record<string, string>>(() => ({
    [REPAIR_SLOPE_POINT_KEYS.latitude]: PointAInfo.MANUAL.latitude,
    [REPAIR_SLOPE_POINT_KEYS.longitude]: PointAInfo.MANUAL.longitude,
    [REPAIR_SLOPE_POINT_KEYS.height]: PointAInfo.MANUAL.height,
  }));

  const { measureResult, requestMeasurementPoint } = useMeasurementResult();
  const [activeInput, setActiveInput] = useState<string | null>(null);
  
  const selectedTitle =
    directionOptions.find((option) => option.id === selectedAPointBucketTeeth)?.title ??
    "";

  const changeLocationMode = (mode: "AUTO"| "MANUAL") => {
    setPointAMode(mode);
    setActiveInput(null);
    // 切换回自动模式，发送测点请求，重新测点
    if (mode === 'AUTO') {
      sendMeasurementPointRequest(selectedAPointBucketTeeth);
    }
  }


  useEffect(() => {
    if (!measureResult) return;
        // 参考点的经纬度和高度
      const { height, longitude, latitude } = measureResult;
      setAPointInfo({
        height,
        longitude,
        latitude
      }, "AUTO");

  }, [measureResult, setAPointInfo]);

  
  // 发送测点请求
const sendMeasurementPointRequest = (point: COMMON_DIRECTION) => {
    // eslint-disable-next-line no-useless-assignment
    let sendPoint = 0x00;
    switch (point) {
      case COMMON_DIRECTION.LEFT:
        sendPoint = 0x00;
        break;
      case COMMON_DIRECTION.MIDDLE:
        sendPoint = 0x01;
        break;
      default:
        sendPoint = 0x02;
    }
    requestMeasurementPoint({
      FeatureID: 0x03,
      PointID: 0x01, // A点
      PointMode: sendPoint,
    });
  }
  
  useEffect(() => {
    sendMeasurementPointRequest(selectedAPointBucketTeeth);
  }, []);

  const activeValue = activeInput ? manualValues[activeInput] ?? "" : "";

  useEffect(() => {
    if (pointA_Mode !== "MANUAL") return;

    setAPointInfo({
      [REPAIR_SLOPE_POINT_KEYS.height]: manualValues[REPAIR_SLOPE_POINT_KEYS.height] ?? "",
      [REPAIR_SLOPE_POINT_KEYS.longitude]: manualValues[REPAIR_SLOPE_POINT_KEYS.longitude] ?? "",
      [REPAIR_SLOPE_POINT_KEYS.latitude]: manualValues[REPAIR_SLOPE_POINT_KEYS.latitude] ?? "",
    }, "MANUAL");
  }, [manualValues, pointA_Mode, setAPointInfo]);

  const changeActiveValue = (value: string) => {
    if (!activeInput) return;

    setManualValues((currentValues) => ({
      ...currentValues,
      [activeInput]: value,
    }));
  };
  return (
    <div className="min-h-screen bg-[#F6F8FD] text-[#111] px-11 py-10">
      <CommonHeader title="直线修坡作业设置" />

      <main className="mt-7 grid grid-cols-2 gap-7">
        <section className="rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-10">
          <div className="m-0 font-semibold leading-none text-black text-[32px]">
            <span>Step2 采集A点：基准线起点</span> 
          </div>
          <div className="mt-5 leading-none text-[#999999] text-2xl">
            <span>将斗齿移动至坡顶线起点，后续将与 B 点共同生成 AB 基准线</span> 
          </div>

            <div className="mt-10 font-medium leading-none text-black text-[32px]">
            <span>采点参考斗齿</span> 
          </div>

          <div className="mt-5 grid grid-cols-3 gap-5">
            {directionOptions.map((option) => {
              const isSelected = selectedAPointBucketTeeth === option.id;

              return (
                <button
                  key={option.id}
                  className={[
                    "flex aspect-256/188 flex-col items-center justify-center rounded-2xl border px-3 transition",
                    "focus:outline-none focus:ring-4 focus:ring-[#006BFF]/20",
                    isSelected
                      ? "border-2 border-[#006BFF] bg-[#EAF0FF]"
                      : "border border-[#D4D4D4] bg-[#0C0C0C05]",
                  ].join(" ")}
                  type="button"
                  onClick={() => {
                    setAPointBucketTeeth(option.id)
                    sendMeasurementPointRequest(option.id)
                  }}
                >
                <div className="absolute -mt-12">
                    <img
                        className="w-76 object-contain"
                        src={isSelected ? option.activeIcon : option.unActiveIcon}
                        alt=""
                    />
                </div>
    
                  <span
                    className={[
                      "mt-24 leading-none text-2xl",
                      isSelected
                        ? "font-medium text-[#006BFF]"
                        : "text-[#444444]",
                    ].join(" ")}
                  >
                    {option.title}
                  </span>
                </button>
              );
            })}
          </div>

          <div className="mt-5 leading-none text-[#777777] text-2xl">
            <span>当前采集基准：{selectedTitle}尖端</span>
          </div>

          <div className="mt-12 font-semibold leading-none text-black text-[32px]">
            <span>定位方式选择</span>
          </div>

          <div className="mt-7 inline-flex rounded-full bg-[#DCDCDC] p-1">
            <button
              className={[
                "rounded-full px-7 py-4 font-semibold leading-none text-2xl",
                pointA_Mode === "AUTO"
                  ? "bg-white text-[#006BFF] shadow-[0_2px_8px_rgba(0,0,0,0.08)]"
                  : "text-white/80",
              ].join(" ")}
              type="button"
              onClick={() => changeLocationMode("AUTO")}
            >
              铲斗采点
            </button>
            <button
              className={[
                "rounded-full px-7 py-4 font-semibold leading-none text-2xl",
                pointA_Mode === "MANUAL"
                  ? "bg-white text-[#006BFF] shadow-[0_2px_8px_rgba(0,0,0,0.08)]"
                  : "text-white/80",
              ].join(" ")}
              type="button"
              onClick={() => changeLocationMode("MANUAL")}
            >
              手动定点
            </button>
          </div>

         {pointA_Mode === "AUTO" ?
          <div className="mt-5 flex flex-col gap-y-3">
            {locationRows.map(([label, key]) => (
              <div
                key={label}
                className="grid grid-cols-[160px_20px_1fr] h-18 items-center  leading-none text-[34px]"
              >
                <span className="text-black">{label}</span>
                <span className="text-black">:</span>
                <span className="text-right text-[#A7AEC0]">{PointAInfo['AUTO']?.[key as REPAIR_SLOPE_POINT_KEYS]} {key === REPAIR_SLOPE_POINT_KEYS.height ? "   m" : ""}</span>
              </div>
            ))}
          </div>  
          : 
            <div className="mt-5 flex flex-col justify-start gap-y-3">
              {locationRows.map(([label, key]) => (
                <div
                    key={label}
                    className="grid grid-cols-[160px_20px_1fr] items-center h-18 leading-none text-[34px]"
                >
                    <span className="text-black">{label}</span>
                    <span className="text-black">:</span>
                    <NumericDisplayButton
                      value={manualValues[key] ?? ""}
                      isActive={activeInput === key}
                      className="ml-16 grid h-[72px] grid-cols-[1fr_auto] items-center rounded-md bg-[#F8FAFF]"
                      valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2]"
                      unit={key === REPAIR_SLOPE_POINT_KEYS.height ? "m" : ""}
                      unitClassName="pr-5 text-[#A7AEC0]"
                      onClick={() => setActiveInput(key as string)}
                    />
                </div>
                ))}
            </div> 
          }
        </section>

        <section className="relative flex flex-col rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-12">
          <p className="m-0 font-semibold leading-none text-black text-[32px]">
            预览
          </p>

          <div className="flex flex-1 items-start justify-center pt-6">
            <img
              className="mt-2 w-[70%]  object-contain"
              src={repairSlopeSelectedType === SlopeSelectedType.bottom ? bottomPointA : topPointA}
              alt=""
            />
          </div>

          {activeInput && (
            <div className="absolute inset-0 z-10 flex items-center justify-center bg-[#F6F8FD]/80">
              <NumericKeyboard
                value={activeValue}
                onChange={changeActiveValue}
                onClear={() => changeActiveValue("")}
                onConfirm={() => setActiveInput(null)}
              />
            </div>
          )}

          <div className="grid grid-cols-2 gap-4">
            <button
              className="h-20 rounded-xl border border-[#E7E7E7] bg-white text-2xl text-black shadow-[0_5px_18px_rgba(15,35,80,0.04)]"
              type="button"
              onClick={() => navigation("../step1")}
            >
              上一步
            </button>
            <button
              className="h-20 rounded-xl bg-[#006BFF] text-2xl font-semibold text-white shadow-[0_8px_20px_rgba(0,77,255,0.22)]"
              type="button"
              onClick={() => navigation("../step3")}
            >
              确定, 下一步
            </button>
          </div>
        </section>
      </main>


        <StepBar length={5} activeStep={1} />

    </div>
  );
};
