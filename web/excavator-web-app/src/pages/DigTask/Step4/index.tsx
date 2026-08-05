import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CommonHeader } from "@/components/CommonHeader";
import { NumericDisplayButton } from "@/components/NumericDisplayButton";
import { NumericKeyboard } from "@/components/NumericKeyboard";
import { StepBar } from "@/components/StepBar";
import ParamsSet from "@/assets/dig/params_set.png";
import { useDigTaskState } from "@/stores/modules/DigTaskStore";
import { SectionEnumType } from "@/types";


const locationRows = [
  ["沟底宽L", SectionEnumType.L_Width],
  ["沟底宽R", SectionEnumType.R_Width],
  ["沟顶宽W", SectionEnumType.W_Width],
  ["沟深H", SectionEnumType.H_Height],
] as const;

export const DigTaskStep4 = () => {
  const navigation = useNavigate();

  const [activeInput, setActiveInput] = useState<SectionEnumType | null>(null);
  const { setSectionParameter, sectionParameter } = useDigTaskState();
  const [manualValues, setManualValues] = useState<Record<SectionEnumType, string>>(() => ({
    [SectionEnumType.L_Width]: sectionParameter.L_Width.toString(),
    [SectionEnumType.R_Width]: sectionParameter.R_Width.toString(),
    [SectionEnumType.W_Width]: sectionParameter.W_Width.toString(),
    [SectionEnumType.H_Height]:sectionParameter.H_Height.toString()
  }));

  const activeValue = activeInput ? manualValues[activeInput] ?? "" : "";

  useEffect(() => {
    setSectionParameter({
      [SectionEnumType.L_Width]: Number(manualValues[SectionEnumType.L_Width]),
      [SectionEnumType.R_Width]: Number(manualValues[SectionEnumType.R_Width]),
      [SectionEnumType.W_Width]: Number(manualValues[SectionEnumType.W_Width]),
      [SectionEnumType.H_Height]: Number(manualValues[SectionEnumType.H_Height]),
    });
  }, [manualValues, setSectionParameter]);

  const changeActiveValue = (value: string) => {
    if (!activeInput) return;

    setManualValues((currentValues) => {
      return {
        ...currentValues,
        [activeInput]: value,
      };
    });
  };

  return (
    <div className="min-h-screen bg-[#F6F8FD] text-[#111] px-11 py-10">
      <CommonHeader title="挖沟作业设置" />

      <main className="mt-7 grid  grid-cols-2 gap-7 min-h-[80vh]">
        <section className="rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] px-10 py-10">
          <div className="m-0 font-semibold leading-none text-black text-[32px]">
            <span>Step4 采集C点：基准线起点</span> 
          </div>
          <div className="mt-5 leading-none text-[#999999] text-2xl">
            <span>将斗齿移动至坡顶线终点，采集后将生成 AB 基准线</span> 
          </div>

          <div className="flex flex-col gap-y-5 mt-10">
              {locationRows.map(([label, key]) => (
              <div
                  key={label}
                  className="grid grid-cols-[160px_20px_1fr] items-center  leading-none text-[34px]"
              >
                  <span className="text-black">{label}</span>
                  <span className="text-black">:</span>
                  <NumericDisplayButton
                    value={manualValues[key] ?? ""}
                    isActive={activeInput === key}
                    className="ml-16 grid h-[72px] grid-cols-[1fr_auto] items-center rounded-md  bg-[#F8FAFF]"
                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2]"
                    unit={"m"}
                    unitClassName="pr-5 text-[#A7AEC0]"
                    onClick={() => setActiveInput(key)}
                  />
                
              </div>
              ))}
          </div>        
        </section>

        <section className="relative flex min-h-[620px] flex-col rounded-2xl bg-white text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] min-h-0 px-10 py-12">
          <p className="m-0 font-semibold leading-none text-black text-[32px]">
            预览
          </p>

          <div className="flex flex-col justify-between h-full ">
            <div className="flex flex-col items-start justify-center -mt-10">
            <div className="flex flex-row justify-center w-full">
              <img
                className="mt-2 w-[70%]  object-contain"
                src={ParamsSet}
                alt=""
              />
            </div>

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
