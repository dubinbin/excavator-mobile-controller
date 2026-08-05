import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CommonHeader } from "@/components/CommonHeader";
import { NumericDisplayButton } from "@/components/NumericDisplayButton";
import { NumericKeyboard } from "@/components/NumericKeyboard";
import { StepBar } from "@/components/StepBar";
import digAbDistanceImage from "@/assets/dig/dig_ab_distance.png";
import { COMMON_DIRECTION, DIG_POINT_KEYS, directionOptions, SPECIAL_INPUT_KEY } from "@/types";
import { useDigTaskState } from "@/stores/modules/DigTaskStore";
import { addDecimalByScale } from "@/utils/helper";
import { useMeasurementResult } from "@/components/useMeasurementResult";
import {
  calculateRtkPointDistance,
  formatMeasurement,
} from "@/utils/repairSlope";


const locationRows = [
    ["经度", DIG_POINT_KEYS.longitude],
    ["纬度", DIG_POINT_KEYS.latitude],
    ["高程", DIG_POINT_KEYS.height],
] as const;

export const DigTaskStep3 = () => {
  const navigation = useNavigate();

  const { setBPointInfo, PointAInfo, PointBInfo, pointA_Mode, pointB_Mode, setPointBMode,setBPointBucketTeeth, selectedBPointBucketTeeth, setABPointDistance } = useDigTaskState();
  const activePointA = PointAInfo[pointA_Mode];
  const activePointB = PointBInfo[pointB_Mode];
  const distanceSourceKey = [
    pointA_Mode,
    activePointA.longitude,
    activePointA.latitude,
    activePointA.height,
    pointB_Mode,
    activePointB.longitude,
    activePointB.latitude,
    activePointB.height,
  ].join("|");
  const calculatedDistance = useMemo(
    () => calculateRtkPointDistance(activePointA, activePointB),
    [activePointA, activePointB],
  );
  const automaticDistanceValue = calculatedDistance === null
    ? ""
    : formatMeasurement(calculatedDistance, 3);
  const [activeInput, setActiveInput] = useState<string | null>(null);
  const [numpadPosition, setNumpadPosition] = useState<COMMON_DIRECTION>(COMMON_DIRECTION.RIGHT);
  const [manualValues, setManualValues] = useState<Record<string, string>>(() => ({
    [DIG_POINT_KEYS.latitude]: PointBInfo.MANUAL.latitude,
    [DIG_POINT_KEYS.longitude]: PointBInfo.MANUAL.longitude,
    [DIG_POINT_KEYS.height]: PointBInfo.MANUAL.height,
  }));
  const [manualDistance, setManualDistance] = useState<{
    sourceKey: string;
    value: string;
  } | null>(null);
  const hasManualDistance = manualDistance?.sourceKey === distanceSourceKey;
  const displayedDistance = hasManualDistance
    ? manualDistance.value
    : automaticDistanceValue;
 const { measureResult, requestMeasurementPoint } = useMeasurementResult();

  const selectedTitle =
    directionOptions.find((option) => option.id === selectedBPointBucketTeeth)?.title ??
    "";

  const changeLocationMode = (mode: "AUTO"| "MANUAL") => {
    setPointBMode(mode);
    setActiveInput(null);
    // 切换回自动模式，发送测点请求，重新测点
    if (mode === 'AUTO') {
      sendMeasurementPointRequest(selectedBPointBucketTeeth);
    }
  }

  useEffect(() => {
    if (!measureResult) return;
          // 参考点的经纬度和高度
        const { height, longitude, latitude } = measureResult;
        setBPointInfo({
          height,
          longitude,
          latitude
        }, "AUTO");

  }, [measureResult, setBPointInfo]);

  const handleInput = (label: string , pos = COMMON_DIRECTION.RIGHT) => {
      setNumpadPosition(pos)
      setActiveInput(label);
  }

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
        FeatureID: 0x02,
        PointID: 0x02, // A点
        PointMode: sendPoint,
      });
    }

  const activeValue = activeInput === SPECIAL_INPUT_KEY.abPointDistance
    ? displayedDistance
    : activeInput
      ? manualValues[activeInput] ?? ""
      : "";

  useEffect(() => {
    if (!hasManualDistance) {
      setABPointDistance(calculatedDistance ?? 0);
    }
  }, [
    calculatedDistance,
    hasManualDistance,
    setABPointDistance,
  ]);

  const manualAdjust = (action: "ADD" | "SUBTRACT") => {
    const INCR = 1;
    const nextValue = addDecimalByScale(
      displayedDistance || 0,
      action === "ADD" ? INCR : -INCR,
      3,
    );

    if (nextValue === "") return;

    setManualDistance({ sourceKey: distanceSourceKey, value: nextValue });
    setABPointDistance(Number(nextValue));
  }

  const changeActiveValue = (value: string) => {
    if (!activeInput) return;

    if (activeInput === SPECIAL_INPUT_KEY.abPointDistance) {
      const numericValue = Number(value);
      setManualDistance({ sourceKey: distanceSourceKey, value });
      setABPointDistance(Number.isFinite(numericValue) ? numericValue : 0);
      return;
    }

    setManualValues((currentValues) => {
      const nextValues = {
        ...currentValues,
        [activeInput]: value,
      };

      if (pointB_Mode === "MANUAL") {
        setBPointInfo({
          [DIG_POINT_KEYS.height]: nextValues[DIG_POINT_KEYS.height] ?? "",
          [DIG_POINT_KEYS.longitude]: nextValues[DIG_POINT_KEYS.longitude] ?? "",
          [DIG_POINT_KEYS.latitude]: nextValues[DIG_POINT_KEYS.latitude] ?? "",
        }, "MANUAL");
      }
      return nextValues;
    });
  };

  // 首次进入页面时发送默认测点请求
  useEffect(() => {
    sendMeasurementPointRequest(selectedBPointBucketTeeth);
  }, [])


  return (
    <div className="min-h-screen bg-[#F6F8FD] text-[#111] px-11 py-10">
      <CommonHeader title="挖沟作业设置" />

      <main className="mt-7 grid grid-cols-2 gap-7">
        <section className="rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-10">
          <div className="m-0 font-semibold leading-none text-black text-[32px]">
            <span>Step3 采集B点：基准线起点</span> 
          </div>
          <div className="mt-5 leading-none text-[#999999] text-2xl">
            <span>将斗齿移动至坡顶线终点，采集后将生成 AB 基准线</span> 
          </div>

          <div className="mt-10 font-semibold leading-none text-black text-[32px]">
            <span>采点参考斗齿</span> 
          </div>

          <div className="mt-5 grid grid-cols-3 gap-5">
            {directionOptions.map((option) => {
              const isSelected = selectedBPointBucketTeeth === option.id;

              return (
                <button
                  key={option.id}
                  className={[
                    "flex aspect-[256/188] flex-col items-center justify-center rounded-2xl border px-3 transition",
                    "focus:outline-none focus:ring-4 focus:ring-[#006BFF]/20",
                    isSelected
                      ? "border-2 border-[#006BFF] bg-[#EAF0FF]"
                      : "border border-[#D4D4D4] bg-[#0C0C0C05]",
                  ].join(" ")}
                  type="button"
                    onClick={() => {
                    setBPointBucketTeeth(option.id)
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
                        ? "font-semibold text-[#006BFF]"
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
                pointB_Mode === "AUTO"
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
                pointB_Mode === "MANUAL"
                  ? "bg-white text-[#006BFF] shadow-[0_2px_8px_rgba(0,0,0,0.08)]"
                  : "text-white/80",
              ].join(" ")}
              type="button"
              onClick={() => changeLocationMode("MANUAL")}
            >
              手动定点
            </button>
          </div>

         {pointB_Mode === "AUTO" ?
       <div className="mt-5 flex flex-col gap-y-3">
            {locationRows.map(([label, key]) => (
              <div
                key={label}
                className="grid grid-cols-[160px_20px_1fr] h-18 items-center  leading-none text-[34px]"
              >
                <span className="text-black">{label}</span>
                <span className="text-black">:</span>
                <span className="text-right text-[#A7AEC0]">{PointBInfo['AUTO']?.[key as DIG_POINT_KEYS]} {key === DIG_POINT_KEYS.height ? "   m" : ""}</span>
              </div>
            ))}
          </div> 
          : 
          <div className="mt-5 flex flex-col gap-y-3">
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
                    unit={key === DIG_POINT_KEYS.height ? "m" : ""}
                    unitClassName="pr-5 text-[#A7AEC0]"
                    onClick={() => setActiveInput(key as string)}
                  />
              </div>
              ))}
          </div> 
          }
        </section>

        <section className="relative flex min-h-[620px] flex-col rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] min-h-0 px-10 py-12">
          <p className="m-0 font-semibold leading-none text-black text-[32px]">
            预览
          </p>

          <div className="flex flex-col justify-between h-full ">
            <div className="flex flex-col items-start justify-center -mt-10">
            <div className="flex flex-row justify-center w-full">
              <img
                className="mt-2 w-[70%] object-contain"
                src={digAbDistanceImage}
                alt=""
              />
            </div>

            <div className="mt-10">
              <p className="font-semibold leading-none text-black text-[32px]">手动微调</p>

              <div className="mt-8 flex flex-row items-center">              
                <span className="text-[32px]"> AB ： </span>
                <div className="flex flex-row items-center gap-x-3">
                 
                  <div className="flex items-center relative">
                    <button onClick={() => manualAdjust("SUBTRACT")} className="shadow-[0_2px_8px_rgba(0,0,0,0.08)] border border-[#EFEFEF] text-3xl bg-white text-[#000000] rounded-full w-11 h-11 flex items-center justify-center">-</button>
                    <NumericDisplayButton
                      value={displayedDistance}
                      isActive={activeInput === SPECIAL_INPUT_KEY.abPointDistance}
                      className="ml-5 mr-2 grid h-18 w-36 grid-cols-[1fr_auto] items-center rounded-md bg-[#F8FAFF] text-3xl text-[#97A4C2]"
                      valueClassName="min-w-0 pl-5 pr-2"
                      unit="m"
                      unitClassName="pr-5"
                      onClick={() => handleInput(SPECIAL_INPUT_KEY.abPointDistance, COMMON_DIRECTION.LEFT)}
                    />
                  </div>
                  <button onClick={() => manualAdjust("ADD")} className="shadow-[0_2px_8px_rgba(0,0,0,0.08)] text-3xl border border-[#EFEFEF] bg-white text-[#000000] rounded-full w-11 h-11  flex items-center justify-center">+</button>
                </div>
              </div>
            </div>
          </div>

          {activeInput && (
             <div className={`${numpadPosition === 'LEFT' ? 'fixed left-1/6 justify-start' : 'left-0'} absolute inset-0 z-10 flex items-center justify-center bg-[#F6F8FD]/80}`}>
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
              onClick={() => navigation("../step2")}
            >
              上一步
            </button>
            <button
              className="h-20 rounded-xl bg-[#006BFF] text-2xl font-semibold text-white shadow-[0_8px_20px_rgba(0,77,255,0.22)]"
              type="button"
              onClick={() => navigation("../step4")}
            >
              确定, 下一步
            </button>
          </div>

          </div>
        </section>
      </main>


      <StepBar length={5} activeStep={2} />
 
    </div>
  );
};
