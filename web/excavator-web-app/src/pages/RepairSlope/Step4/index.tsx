import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CommonHeader } from "@/components/CommonHeader";
import { NumericDisplayButton } from "@/components/NumericDisplayButton";
import { NumericKeyboard } from "@/components/NumericKeyboard";
import { StepBar } from "@/components/StepBar";
import bottomPointC from "@/assets/repaire-slope/step4_bottom_1.png"
import topPointC from "@/assets/repaire-slope/step4_top_1.png"

import bottomPointCLeft from "@/assets/repaire-slope/step4_bottom_2.png"
import topPointCLeft from "@/assets/repaire-slope/step4_top_2.png"
import { COMMON_DIRECTION, directionOptions, REPAIR_SLOPE_POINT_KEYS, SlopeSelectedType, SPECAIL_INPUT_TYPE_SLOPE } from "@/types";
import { useRepairSlopeStore } from "@/stores/modules/RepairSlopeStore";
import { addDecimalByScale } from "@/utils/helper";
import { useMeasurementResult } from "@/components/useMeasurementResult";


const directionOfSlopeOptions = [
    {
        id: "LEFT",
        title: "左侧",
    },
    {
        id: "RIGHT",
        title: "右侧",
    },
] as const;


const locationRows = [
    ["经度", REPAIR_SLOPE_POINT_KEYS.longitude],
    ["纬度", REPAIR_SLOPE_POINT_KEYS.latitude],
    ["高程", REPAIR_SLOPE_POINT_KEYS.height],
] as const;

export const RepairSlopeStep4 = () => {
  const navigation = useNavigate();

  const { repairSlopeSelectedType, PointCInfo, pointC_Mode, setPointCMode, setCPointInfo, setCPointBucketTeeth, setSectionParameter, selectedCPointBucketTeeth, sectionParameter } = useRepairSlopeStore();

  const [activeInput, setActiveInput] = useState<string | null>(null);

  const { SLOPE_TYPE } = sectionParameter;

  const [numpadPosition, setNumpadPosition] = useState<COMMON_DIRECTION>(COMMON_DIRECTION.RIGHT);

  const selectedTitle =
    directionOptions.find((option) => option.id === selectedCPointBucketTeeth)?.title ??
    "";

  const [manualValues, setManualValues] = useState<Record<string, string>>(() => ({
    [REPAIR_SLOPE_POINT_KEYS.latitude]: PointCInfo.MANUAL.latitude,
    [REPAIR_SLOPE_POINT_KEYS.longitude]: PointCInfo.MANUAL.longitude,
    [REPAIR_SLOPE_POINT_KEYS.height]: PointCInfo.MANUAL.height,
    ...sectionParameter,
  }));
  const { measureResult, requestMeasurementPoint } = useMeasurementResult();
  const changeLocationMode = (mode: "AUTO"| "MANUAL") => {
    setPointCMode(mode);
    setActiveInput(null);
    // 切换回自动模式，发送测点请求，重新测点
    if (mode === 'AUTO') {
      sendMeasurementPointRequest(selectedCPointBucketTeeth);
    }
  }

  const activeValue = activeInput ? manualValues[activeInput] ?? "" : "";

  useEffect(() => {
    setSectionParameter({
      [SPECAIL_INPUT_TYPE_SLOPE.L_Width]: manualValues[SPECAIL_INPUT_TYPE_SLOPE.L_Width],
      [SPECAIL_INPUT_TYPE_SLOPE.AB_Width]: manualValues[SPECAIL_INPUT_TYPE_SLOPE.AB_Width],
      [SPECAIL_INPUT_TYPE_SLOPE.H_Width]: manualValues[SPECAIL_INPUT_TYPE_SLOPE.H_Width],
      [SPECAIL_INPUT_TYPE_SLOPE.SLOPE_TYPE]: sectionParameter.SLOPE_TYPE,
    });
  }, [manualValues, sectionParameter.SLOPE_TYPE, setSectionParameter]);

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
        FeatureID: 0x03,
        PointID: 0x03, // C点
        PointMode: sendPoint,
      });
    }

  useEffect(() => {
    sendMeasurementPointRequest(selectedCPointBucketTeeth);
  }, []);
  const changeActiveValue = (value: string) => {
    if (!activeInput) return;

    setManualValues((currentValues) => {
      const nextValues = {
        ...currentValues,
        [activeInput]: value,
      };

      if (pointC_Mode === "MANUAL") {
        setCPointInfo({
          [REPAIR_SLOPE_POINT_KEYS.height]: nextValues[REPAIR_SLOPE_POINT_KEYS.height] ?? "",
          [REPAIR_SLOPE_POINT_KEYS.longitude]: nextValues[REPAIR_SLOPE_POINT_KEYS.longitude] ?? "",
          [REPAIR_SLOPE_POINT_KEYS.latitude]: nextValues[REPAIR_SLOPE_POINT_KEYS.latitude] ?? "",
        }, 'MANUAL');
      }

      switch (activeInput) {
        case SPECAIL_INPUT_TYPE_SLOPE.AB_Width:
          setSectionParameter({
            ...sectionParameter,
            [SPECAIL_INPUT_TYPE_SLOPE.AB_Width]: value
          });
          break;
        case SPECAIL_INPUT_TYPE_SLOPE.H_Width:
          setSectionParameter({
            ...sectionParameter,
            [SPECAIL_INPUT_TYPE_SLOPE.H_Width]: value
          });
          break;
        case SPECAIL_INPUT_TYPE_SLOPE.L_Width:
          setSectionParameter({
            ...sectionParameter,
            [SPECAIL_INPUT_TYPE_SLOPE.L_Width]: value
          });
          break;
      }
      return nextValues;
    });
  };

  useEffect(() => {
    if (!measureResult) return;
          // 参考点的经纬度和高度
        const { height, longitude, latitude } = measureResult;
        setCPointInfo({
          height,
          longitude,
          latitude
        }, "AUTO");

  }, [measureResult, setCPointInfo]);

  const setSelectedDirectionOfSlope = (direction: "LEFT"|"RIGHT") => {
        setSectionParameter({
          ...sectionParameter,
          [SPECAIL_INPUT_TYPE_SLOPE.SLOPE_TYPE]: direction
        });
  }

  const handleInput = (label: string , pos = COMMON_DIRECTION.RIGHT) => {
      setNumpadPosition(pos)
      setActiveInput(label);
  }


  const manualAdjust = (action: "ADD" | "SUBTRACT", type: SPECAIL_INPUT_TYPE_SLOPE) => {
    const INCR = 1;
    const nextValue = addDecimalByScale(
      manualValues[type],
      action === "ADD" ? INCR : -INCR,
    );

    if (nextValue === "") return;

    setManualValues((currentValues) => ({
      ...currentValues,
      [type]: nextValue,
    }));


    switch (activeInput) {
      case SPECAIL_INPUT_TYPE_SLOPE.AB_Width:
        setSectionParameter({
          ...sectionParameter,
          [SPECAIL_INPUT_TYPE_SLOPE.AB_Width]: nextValue
        });
        break;
      case SPECAIL_INPUT_TYPE_SLOPE.H_Width:
        setSectionParameter({
          ...sectionParameter,
          [SPECAIL_INPUT_TYPE_SLOPE.H_Width]: nextValue
        });
        break;
      case SPECAIL_INPUT_TYPE_SLOPE.L_Width:
        setSectionParameter({
          ...sectionParameter,
          [SPECAIL_INPUT_TYPE_SLOPE.L_Width]: nextValue
        });
        break;
    }
  }

  

  return (
    <div className="min-h-screen bg-[#F6F8FD]  text-[#111] px-11 py-10">
      <CommonHeader title="直线修坡作业设置" />

      <main className="mt-7 grid grid-cols-2 gap-7">
        <section className="rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-10">
          <div className="m-0 font-semibold leading-none text-black text-[32px]">
            <span>Step4 采集C点：基准线起点</span> 
          </div>
          <div className="mt-5 leading-none text-[#999999] text-2xl">
            <span>将斗齿移动至坡顶线终点，采集后将生成 AB 基准线</span> 
          </div>

          <div className="mt-10 font-semibold leading-none text-black text-[32px]">
            <span>采点参考斗齿</span> 
          </div>

          <div className="mt-5 grid grid-cols-3 gap-5">
            {directionOptions.map((option) => {
              const isSelected = selectedCPointBucketTeeth === option.id;

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
                    setCPointBucketTeeth(option.id)
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
                "rounded-full px-7 py-4  font-semibold leading-none text-2xl",
                pointC_Mode === "AUTO"
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
                pointC_Mode === "MANUAL"
                  ? "bg-white text-[#006BFF] shadow-[0_2px_8px_rgba(0,0,0,0.08)]"
                  : "text-white/80",
              ].join(" ")}
              type="button"
              onClick={() => changeLocationMode("MANUAL")}
            >
              手动定点
            </button>
          </div>

         {pointC_Mode === "AUTO" ?
          <div className="mt-5 flex flex-col gap-y-3">
           {locationRows.map(([label, key]) => (
              <div
                key={label}
                className="grid grid-cols-[160px_20px_1fr] h-18 items-center  leading-none text-[34px]"
              >
                <span className="text-black">{label}</span>
                <span className="text-black">:</span>
                <span className="text-right text-[#A7AEC0]">{PointCInfo['AUTO']?.[key as REPAIR_SLOPE_POINT_KEYS]} {key === REPAIR_SLOPE_POINT_KEYS.height ? "   m" : ""}</span>
              </div>
            ))}
          </div> 
          : 
            <div className="mt-5 flex flex-col gap-y-3">
              {locationRows.map(([label, key]) => (
                <div
                    key={label}
                    className="grid grid-cols-[160px_20px_1fr]  h-18 items-center  leading-none text-[34px]"
                >
                    <span className="text-black">{label}</span>
                    <span className="text-black">:</span>
                    <NumericDisplayButton
                      value={manualValues[key] ?? ""}
                      isActive={activeInput === label}
                      className="ml-16 grid h-[72px] grid-cols-[1fr_auto] items-center rounded-md bg-[#F8FAFF]"
                      valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2]"
                      unit={key === REPAIR_SLOPE_POINT_KEYS.height ? "m" : ""}
                      
                      unitClassName="pr-5 text-[#A7AEC0]"
                      onClick={() => handleInput(key)}
                    />
                  
                </div>
                ))}
            </div> 
          }
        </section>

        <section className="relative flex min-h-[620px] flex-col rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-12">
          <p className="m-0 font-semibold leading-none text-black text-[32px]">
            预览
          </p>

          <div className="flex flex-col justify-between h-full ">
            <div className="flex flex-col items-start justify-center -mt-10">
            <div className="flex flex-row justify-center w-full">
              {
                SLOPE_TYPE === "LEFT" ? <img
                className="-mt-2 w-[70%] object-contain"
                src={repairSlopeSelectedType === SlopeSelectedType.top ? topPointCLeft : bottomPointCLeft }
                alt=""
              /> : 
               <img
                className="-mt-2 w-[70%] object-contain"
                src={repairSlopeSelectedType === SlopeSelectedType.top ? topPointC : bottomPointC }
                alt=""
              />
              }
             
            </div>



            <div className="mt-10">
              <p className="font-semibold leading-none text-black text-[32px]">坡面方向</p>


            <div className="flex flex-row mt-5 gap-x-5">
                {directionOfSlopeOptions.map((option) => {
                const isSelected = sectionParameter.SLOPE_TYPE === option.id;

                return (
                    <button
                        key={option.id}
                        className={[
                            "flex flex-col items-center justify-center rounded-2xl border px-3 transition",
                            "focus:outline-none focus:ring-4 focus:ring-[#006BFF]/20",
                            isSelected
                            ? "border-2 border-[#006BFF] bg-[#EAF0FF]"
                            : "border border-[#D4D4D4] bg-[#0C0C0C05]",
                        ].join(" ")}
                        type="button"
                        onClick={() => setSelectedDirectionOfSlope(option.id)}
                        >
                        <span
                            className={[
                            "leading-none text-2xl py-3 px-6",
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


                <div className="grid grid-cols-2 mt-4 gap-x-6">
                    <div className="mt-2 flex flex-row items-center">              
                        <span className="text-[32px] w-18">AB</span>
                        <span className="text-[32px] w-18">：</span>
                        <div className="flex flex-row items-center gap-x-3">
                        
                        <div className="flex items-center relative">
                            <button onClick={() => manualAdjust("SUBTRACT", SPECAIL_INPUT_TYPE_SLOPE.AB_Width)} className="shadow-[0_2px_8px_rgba(0,0,0,0.08)] border border-[#EFEFEF] text-3xl bg-white text-[#000000] rounded-full w-11 h-11 flex items-center justify-center">-</button>
                            <NumericDisplayButton
                              value={manualValues.AB_Width ?? ""}
                              isActive={activeInput === SPECAIL_INPUT_TYPE_SLOPE.AB_Width}
                              className="ml-5 mr-2 grid h-18 w-30 grid-cols-[1fr_auto] items-center rounded-md bg-[#F8FAFF] text-2xl text-[#97A4C2]"
                              valueClassName="min-w-0 pl-4 pr-2"
                              unit="m"
                              unitClassName="pr-5"
                              onClick={() => handleInput(SPECAIL_INPUT_TYPE_SLOPE.AB_Width, COMMON_DIRECTION.LEFT)}
                            />
                        </div>
                        <button onClick={() => manualAdjust("ADD", SPECAIL_INPUT_TYPE_SLOPE.AB_Width)} className="shadow-[0_2px_8px_rgba(0,0,0,0.08)] text-3xl border border-[#EFEFEF] bg-white text-[#000000] rounded-full w-11 h-11  flex items-center justify-center">+</button>
                        </div>
                    </div>

                    <div className="mt-2 flex flex-row items-center">              
                        <span className="text-[32px] w-18">H</span>
                        <span className="text-[32px] w-18">：</span>
                        <div className="flex flex-row items-center gap-x-3">
                        
                        <div className="flex items-center relative">
                            <button onClick={() => manualAdjust("SUBTRACT", SPECAIL_INPUT_TYPE_SLOPE.H_Width)} className="shadow-[0_2px_8px_rgba(0,0,0,0.08)] border border-[#EFEFEF] text-3xl bg-white text-[#000000] rounded-full w-11 h-11 flex items-center justify-center">-</button>
                            <NumericDisplayButton
                              value={manualValues.H_Width ?? ""}
                              isActive={activeInput === SPECAIL_INPUT_TYPE_SLOPE.H_Width}
                              className="ml-5 mr-2 grid h-18 w-30 grid-cols-[1fr_auto] items-center rounded-md bg-[#F8FAFF] text-2xl text-[#97A4C2]"
                              valueClassName="min-w-0 pl-4 pr-2"
                              unit="m"
                              unitClassName="pr-5"
                              onClick={() => handleInput(SPECAIL_INPUT_TYPE_SLOPE.H_Width, COMMON_DIRECTION.LEFT)}
                            />
                        </div>
                        <button onClick={() => manualAdjust("ADD", SPECAIL_INPUT_TYPE_SLOPE.H_Width)} className="shadow-[0_2px_8px_rgba(0,0,0,0.08)] text-3xl border border-[#EFEFEF] bg-white text-[#000000] rounded-full w-11 h-11  flex items-center justify-center">+</button>
                        </div>
                    </div>


                    <div className="mt-2 flex flex-row items-center">              
                        <span className="text-[32px] w-18">L</span>
                        <span className="text-[32px] w-18">：</span>
                        <div className="flex flex-row items-center gap-x-3">
                        
                        <div className="flex items-center relative">
                            <button onClick={() => manualAdjust("SUBTRACT", SPECAIL_INPUT_TYPE_SLOPE.L_Width)} className="shadow-[0_2px_8px_rgba(0,0,0,0.08)] border border-[#EFEFEF] text-3xl bg-white text-[#000000] rounded-full w-11 h-11 flex items-center justify-center">-</button>
                            <NumericDisplayButton
                              value={manualValues.L_Width ?? ""}
                              isActive={activeInput === SPECAIL_INPUT_TYPE_SLOPE.L_Width}
                              className="ml-5 mr-2 grid h-18 w-30 grid-cols-[1fr_auto] items-center rounded-md bg-[#F8FAFF] text-2xl text-[#97A4C2]"
                              valueClassName="min-w-0 pl-4 pr-2"
                              unit="m"
                              unitClassName="pr-5"
                              onClick={() => handleInput(SPECAIL_INPUT_TYPE_SLOPE.L_Width, COMMON_DIRECTION.LEFT)}
                            />
                        </div>
                        <button onClick={() => manualAdjust("ADD", SPECAIL_INPUT_TYPE_SLOPE.L_Width)} className="shadow-[0_2px_8px_rgba(0,0,0,0.08)] text-3xl border border-[#EFEFEF] bg-white text-[#000000] rounded-full w-11 h-11  flex items-center justify-center">+</button>
                        </div>
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
              onClick={() => navigation("../step3")}
            >
              上一步
            </button>
            <button
              className="h-20 rounded-xl bg-[#006BFF] text-2xl font-semibold text-white shadow-[0_8px_20px_rgba(0,77,255,0.22)]"
              type="button"
              onClick={() => navigation("../finished")}
            >
              确定, 下一步
            </button>
          </div>

          </div>
        </section>
      </main>


        <StepBar length={5} activeStep={3} />

    </div>
  );
};
