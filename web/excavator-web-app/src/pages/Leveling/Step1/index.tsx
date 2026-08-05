

// 1. 遥控器发送功能选择帧，`MsgID = 0x04`（功能选择 / 退出），`FeatureID = 0x01`（找平），`Action = 0x01`（进入），进入找平功能。
// 2. TCU 回应功能选择应答帧，`MsgID = 0x84`，遥控器以 `ActiveFeature = 0x01`（找平）作为进入找平功能成功的判据。
// 3. 遥控器发送通用测点请求帧，`MsgID = 0x10`：
//    - `FeatureID = 0x01`（找平）
//    - `PointID = 0x00`（通用参考点）
//    - `PointMode = 左斗尖 / 中斗尖 / 右斗尖 `
// 4. TCU 回传测点结果帧，`MsgID = 0x90`，返回该点高度以及经纬度。
// 5. 遥控器在本地根据参考点高度完成目标高度配置。
// 6. 用户最终确认后，遥控器发送找平参数整包下发帧，`MsgID = 0x11`。
// 7. TCU 回传找平参数应答帧，`MsgID = 0x91`，确认最终采用的目标高度。
// 8. 遥控器发送任务确认帧，`MsgID = 0x40`，`FeatureID = 0x01`（找平），`Action = 0x01`（确认任务开始生效）。
// 9. TCU 回传任务确认应答帧，`MsgID = 0xC0`，找平任务进入激活状态。

import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CommonHeader } from "@/components/CommonHeader";
import { NumericKeyboard } from "@/components/NumericKeyboard";
import { StepBar } from "@/components/StepBar";
import ExcavatorIcon from "@/assets/excavator.png";
import { useLevelingStore } from "@/stores/modules/LevelingStore";
import { directionOptions, IEventCode, TARGET_SET_MODE } from "@/types";
import { useMeasurementResult } from "@/components/useMeasurementResult";
import { onAndroidMessage, sendToAndroid } from "@/utils/bridge";


const inputRows = [
  { id: 'targetAltitude', label: "目标高度", key: "targetAltitude"},
  { id: "digSize", label: "填挖量", key: "digSize" },
] as const;

const inputRowsInManual = [
  { id: "targetLongitude", label: "目标经度", key:" targetLongitude" },
  { id: "targetLatitude", label: "目标纬度", key: "targetLatitude" },
  { id: "digSize", label: "填挖量", key: "digSize" },
] as const;

type NumericInputId =
  | (typeof inputRows)[number]["id"]
  | (typeof inputRowsInManual)[number]["id"];

export const LevelingStep1 = () => {
  const navigation = useNavigate();
  const [activeInput, setActiveInput] = useState<NumericInputId | null>(null);
const { measureResult, requestMeasurementPoint } = useMeasurementResult();
const [currentHeight, setCurrentHeight] = useState(0);

  const { 
    setCurrentFixationMode, 
    currentFixationMode, 
    setBucketPos, 
    bucketPos, 
    currentReferencePoint,
    setCurrentReferencePoint,
    digSize,
    targetAltitude,
    setTargetAltitude,
    targetLatitude,
    setDigSize,
    targetLongitude,
    setTargetLongitudeAndLatitude
  } = useLevelingStore();

  const changeLocationMode = (mode: "ALTITUDE" | "COORDINATE") => {
    setActiveInput(null);
    setCurrentFixationMode(mode);
  }

  useEffect(() => {
  if (!measureResult) return;
    setCurrentReferencePoint(measureResult.height.toString());

  }, [measureResult, setCurrentReferencePoint]);

  const getInputValue = (inputId: NumericInputId) => {
    switch (inputId) {
      case "digSize":
        return digSize;
      case 'targetAltitude':
        return targetAltitude;
      case "targetLongitude":
        return targetLongitude;
      case "targetLatitude":
        return targetLatitude;
    }
  };

  const activeValue = activeInput ? getInputValue(activeInput) : "";

  const changeActiveValue = (value: string) => {
    switch (activeInput) {
      case "digSize":
        setDigSize(value)
        break;
      case 'targetAltitude':
        setTargetAltitude(value)
        break;
      case "targetLongitude":
        setTargetLongitudeAndLatitude(value, undefined);
        break;
      case "targetLatitude":
        setTargetLongitudeAndLatitude(undefined, value);
        break;
    }
  };
  const clearActiveValue = () => {
    changeActiveValue("");
  };

  const confirmActiveValue = () => {
    setActiveInput(null);
    requestCalcDigHegiht();
  };

  // 获取imu计算的找平距离底部真实的高度
  const requestCalcDigHegiht = () => {
    try {
      sendToAndroid(IEventCode.GET_LEVEL_CALC_DIG_AMOUNT_SIGNAL, {
         mode: currentFixationMode === 'ALTITUDE' ? TARGET_SET_MODE.HEIGHT_MODE : TARGET_SET_MODE.COORDINATE_MODE,
         // 目标高度
         targetHeight: targetAltitude,
         // 填挖量
         digMagnitude: digSize,
        // 目标经度
        targetLongitude: targetLongitude,
        // 目标纬度
        targetLatitude: targetLatitude

      })
    } catch (err) {
      console.error(err)
    }
  }
 
  // 发送测点请求
  const sendMeasurementPointRequest = (point: "LEFT"|"MIDDLE"|"RIGHT") => {
    // eslint-disable-next-line no-useless-assignment
    let sendPoint = 0x00;
    switch (point) {
      case "LEFT":
        sendPoint = 0x00;
        break;
      case "MIDDLE":
        sendPoint = 0x01;
        break;
      default:
        sendPoint = 0x02;
    }
    requestMeasurementPoint({
      FeatureID: 0x01,
      PointID: 0x00,
      PointMode: sendPoint,
    });
  }


  // 首次进入页面时发送默认测点请求
  useEffect(() => {
    sendMeasurementPointRequest(bucketPos);
  }, [])


    useEffect(() => {
      const unsubscribe = onAndroidMessage(
        IEventCode.RECEIVE_LEVEL_CALC_DIG_AMOUNT,
        (payload) => {
          const result = payload as { height: number };
          setCurrentHeight(result?.height || 0);
        },
      );
  
      return unsubscribe;
    }, []);
  

  return (
    <div className="min-h-screen bg-[#F6F8FD] text-[#111] px-11 py-10">
      <CommonHeader title="找平作业设置" />

      <main className="mt-7 grid gap-6 grid-cols-2 gap-7">
        <section className="rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-10">
          <div className="m-0  font-semibold leading-none text-black text-[32px]">
            <span>Step1 参考点</span> 
          </div>

          <div className="mt-6 grid grid-cols-3 gap-5">
            {directionOptions.map((option) => {
              const isSelected = bucketPos === option.id;

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
                    setBucketPos(option.id);
                    sendMeasurementPointRequest(option.id);
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

          <div className="mt-24 font-semibold leading-none text-black text-[32px]">
            <span>Step2 目标设定</span>
          </div>

          <div className="mt-7 inline-flex rounded-full bg-[#DCDCDC] p-1">
            <button
              className={[
                "rounded-full px-7 py-4 font-semibold leading-none text-2xl",
                currentFixationMode === "ALTITUDE"
                  ? "bg-white text-[#006BFF] shadow-[0_2px_8px_rgba(0,0,0,0.08)]"
                  : "text-white/80",
              ].join(" ")}
              type="button"
              onClick={() => changeLocationMode("ALTITUDE")}
            >
              高度采点
            </button>
            <button
              className={[
                "rounded-full px-7 py-4 font-semibold leading-none text-2xl",
                currentFixationMode === "COORDINATE"
                  ? "bg-white text-[#006BFF] shadow-[0_2px_8px_rgba(0,0,0,0.08)]"
                  : "text-white/80",
              ].join(" ")}
              type="button"
              onClick={() => changeLocationMode("COORDINATE")}
            >
              坐标定点
            </button>
          </div>
         {currentFixationMode === "ALTITUDE" ? 
          <div className="mt-6 flex flex-col gap-y-3">
            <div className="grid h-18 grid-cols-[200px_20px_1fr] h-18s items-center leading-none text-[34px]">
              <span className="text-black">当前参考点</span>
              <span className="text-black">:</span>
              <div className="ml-16 grid grid-cols-[1fr_auto] items-center">
                <span className="text-left text-[#A7AEC0]">{currentReferencePoint}</span>
                <span className="text-[#8F9BB8] mr-7">m</span>
              </div>
            </div>

            {inputRows.map((row) => {
              const value = getInputValue(row.id);
              const isActive = activeInput === row.id;

              return (
                <div
                  key={row.id}
                  className="grid grid-cols-[200px_20px_1fr] h-18 items-center leading-none text-[34px]"
                >
                  <span className="text-black">{row.label}</span>
                  <span className="text-black">:</span>
                  <button
                    type="button"
                    onClick={() => setActiveInput(row.id)}
                    className={[
                      "ml-16 grid h-[72px] grid-cols-[1fr_auto] items-center rounded-xl border bg-[#F8FAFF] text-left",
                      isActive
                        ? "border-[#006BFF] ring-2 ring-[#006BFF]/10"
                        : "border-[#E3EBFF]",
                    ].join(" ")}
                  >
                    <span className="min-w-0 px-7 text-[#8F9BB8]">
                      {value}
                    </span>
                    <span className="pr-7 text-[#8F9BB8]">m</span>
                  </button>
                </div>
              );
            })}

            <div className="grid opacity-0 h-18 grid-cols-[200px_20px_1fr] items-center leading-none text-[34px]">
              <span className="text-black">****</span>
              <span className="text-black">:</span>
              <div className="ml-16 grid grid-cols-[1fr_auto] items-center">
                <span className="text-left text-[#A7AEC0]">0</span>
                <span className="pr-7 text-[#8F9BB8]">m</span>
              </div>
            </div>
          </div> : 


          <div className="mt-6 flex flex-col gap-y-3">
            <div className="grid h-18 grid-cols-[200px_20px_1fr] items-center leading-none text-[34px]">
              <span className="text-black">当前经纬度</span>
              <span className="text-black">:</span>
              <div className="ml-16 grid grid-cols-[1fr_auto] items-center">
                <span className="text-right text-[#A7AEC0]">{`${measureResult?.longitude || '0'}, ${measureResult?.latitude || '0'}`}</span>
              </div>
            </div>

            {inputRowsInManual.map((row) => {
              const value = getInputValue(row.id);
              const isActive = activeInput === row.id;

              return (
                <div
                  key={row.id}
                  className="grid grid-cols-[200px_20px_1fr] items-center leading-none text-[34px]"
                >
                  <span className="text-black">{row.label}</span>
                  <span className="text-black">:</span>
                  <button
                    type="button"
                    onClick={() => setActiveInput(row.id)}
                    className={[
                      "ml-16 grid h-18 grid-cols-[1fr_auto] items-center rounded-xl border bg-[#F8FAFF] text-left",
                      isActive
                        ? "border-[#006BFF] ring-2 ring-[#006BFF]/10"
                        : "border-[#E3EBFF]",
                    ].join(" ")}
                  >
                    <span className="min-w-0 px-7 text-[#8F9BB8]">
                      {value}
                    </span>
                  </button>
                </div>
              );
            })}
          </div> 
         }
        </section>

        <section className="relative flex min-h-155 flex-col rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-12">
          <p className="m-0 font-semibold leading-none text-black text-[32px]">
            预览
          </p>

          <div className="flex flex-1 items-start justify-center pt-6">
            <img
              className="mt-2 w-full absolute object-contain "
              src={ExcavatorIcon}
              alt=""
            />
            <p className="absolute bottom-[40%] text-[#FF0000] left-[81%] text-3xl font-medium">{currentHeight}m</p>
          </div>

          {activeInput && (
            <div className="absolute inset-0 z-10 flex items-center justify-center bg-[#F6F8FD]/80">
              <NumericKeyboard
                value={activeValue.toString()}
                onChange={changeActiveValue}
                onClear={clearActiveValue}
                onConfirm={confirmActiveValue}
              />
            </div>
          )}

          <div className="grid grid-cols-1 gap-4">
            <button
              className="h-20 rounded-xl bg-[#006BFF] text-2xl font-semibold text-white shadow-[0_8px_20px_rgba(0,77,255,0.22)]"
              type="button"
              onClick={() => navigation("../finished")}
            >
              确定, 下一步
            </button>
          </div>
        </section>
      </main>


        <StepBar length={2} activeStep={0} />
      
    </div>
  );
};
