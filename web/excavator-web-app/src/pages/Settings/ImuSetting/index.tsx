
import { useCallback, useEffect, useLayoutEffect, useState } from "react";
import ExcavatorImuPos from "@/assets/setting/excavator_example.png"
import { NumericDisplayButton } from "@/components/NumericDisplayButton";
import { NumericKeyboard } from "@/components/NumericKeyboard";
import { CommonToast } from "@/components/CommonToast";
import { onAndroidMessage, sendToAndroid } from "@/utils/bridge";
import { IEventCode } from "@/types";
import { useSettingStore } from "@/stores/modules/SettingStore";

const imuConfigs = [
  {
    key: "imu1",
    title: "大臂",
    sensorId: "IMU-01",
    label: "安装偏移角度 θ",
    description: "大臂IMU相对设计位置的安装偏差",
  },
  {
    key: "imu2",
    title: "小臂",
    sensorId: "IMU-02",
    label: "安装偏移角度 α",
    description: "小臂IMU相对设计位置的安装偏差",
  },
  {
    key: "imu3",
    title: "铲斗",
    sensorId: "IMU-03",
    label: "安装偏移角度 β",
    description: "铲斗IMU相对设计位置的安装偏差",
  },
] as const;

export const ImuSetting = () => {
  const [activeInput, setActiveInput] = useState<string | null>(null);
  const { imuSetting, setImuSetting, setSaveAction } = useSettingStore();
  const manualValues = imuSetting;
  const activeValue = activeInput ? manualValues[activeInput] ?? "" : "";
    const changeActiveValue = (value: string) => {
    if (!activeInput) return;
      setImuSetting({
          [activeInput]: value,
      });
  };

  useEffect(() => {
    sendToAndroid(IEventCode.GET_IMU_INFO_SETTING_SAVED_SIGNAL);
    const unsubscribe = onAndroidMessage(IEventCode.GET_IMU_INFO_SETTING_SAVED, (payload) => {
        if (payload) {
          setImuSetting({
              ...payload
          })
        }
    });

    return unsubscribe;
  }, [setImuSetting]);

  const sendSavingImuInfo = useCallback(() => {
      console.error(imuSetting)
        sendToAndroid(IEventCode.IMU_INFO_SETTING_SAVED_SIGNAL, {
          ...imuSetting,
        });

      CommonToast.success("IMU配置已更新");
  }, [imuSetting]);

  
  useLayoutEffect(() => {
    setSaveAction(sendSavingImuInfo);

    return () => {
      setSaveAction(() => {});
    };
  }, [sendSavingImuInfo, setSaveAction]);


  return (
    <div>
      <header>
        <div className="text-[40px] font-semibold leading-tight text-black">
          IMU 安装角度偏差
        </div>
        <div className="mt-3 text-[24px] leading-none text-[#8C8F96]">
           <p>校正各关节IMU传感器的安装物理偏移量。正值为顺时针，负值为逆时针。</p>
        </div>
      </header>

      <section className="mt-12 grid grid-cols-[minmax(560px,740px)_minmax(480px,640px)] gap-9">
        <div className="grid gap-5">
          {imuConfigs.map((item) => (
            <article
              key={item.key}
              className="grid min-h-[228px] grid-cols-[1fr_240px] rounded-2xl bg-white px-10 py-9 shadow-[0_14px_34px_rgba(24,68,145,0.06)]"
            >
              <div>
                <div className="text-[32px] font-semibold leading-none text-black">{item.title}</div>
                <div className="mt-8 text-[30px] font-medium leading-none text-black">{item.label}</div>
                <div className="mt-4 text-[24px] leading-none text-[#B2B4BA]">{item.description}</div>
              </div>

              <div className="flex flex-col items-end">
                <div className="text-[24px] leading-none text-[#A5A8AE]">
                  传感器ID: {item.sensorId}
                </div>
                <label className="relative mt-9 block">
                <NumericDisplayButton
                    value={manualValues[item.key] ?? 0 }
                    isActive={activeInput === item.key}
                    className="ml-16 grid w-24 overflow-hidden h-[72px] grid-cols-[1fr_auto] items-center rounded-2xl bg-[#F8FAFF]"
                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                    unit=""
                    unitClassName="pr-5 text-[#A7AEC0]"
                    onClick={() => setActiveInput(item.key)}
                    />
                  <span className="absolute -right-5 top-3 text-[24px] leading-none text-[#A5A8AE]">°</span>
                </label>
              </div>
            </article>
          ))}
        </div>

        <div className="flex min-h-[718px] relative items-center justify-center rounded-2xl bg-white shadow-[0_14px_34px_rgba(24,68,145,0.06)]">
          <img className="w-5/6" src={ExcavatorImuPos} alt="" />

            {activeInput && (
                <div className="absolute inset-0 z-10 flex items-center justify-center bg-[#F6F8FD]/80 scale-80">
                    <NumericKeyboard
                        value={activeValue}
                        onChange={changeActiveValue}
                        onClear={() => changeActiveValue("")}
                        onConfirm={() => setActiveInput(null)}
                    />
                </div>
            )}
        </div>
      </section>
    </div>
  );
};

