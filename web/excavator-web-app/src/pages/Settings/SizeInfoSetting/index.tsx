
import { useCallback, useEffect, useLayoutEffect, useState } from "react";
import Cat303Image from "@/assets/setting/cat_303.png";
import Cat312Image from "@/assets/setting/cat_312.png";
import Cat320Image from "@/assets/setting/cat_320.png";
import Cat330Image from "@/assets/setting/cat_330.png";
import Cat336Image from "@/assets/setting/cat_336.png";
import { UserDefaultTab } from "./UserDefaultTab";
import { IEventCode, IUserDefaultSizeKey } from "@/types";
import { useSettingStore } from "@/stores/modules/SettingStore";
import { onAndroidMessage, sendToAndroid } from "@/utils/bridge";
import { CommonToast } from "@/components/CommonToast";

const machineOptions = [
  {
    id: "cat303",
    name: "CAT 303",
    weight: "3.4t",
    arm: "2.9 m",
    forearm: "1.5 m",
    image: Cat303Image,
  },
  {
    id: "cat320",
    name: "CAT 320",
    weight: "3.4t",
    arm: "5.8 m",
    forearm: "2.9 m",
    image: Cat320Image,
  },
  {
    id: "cat330",
    name: "CAT 330",
    weight: "3.4t",
    arm: "6.4 m",
    forearm: "3.2 m",
    image: Cat330Image,
  },
  {
    id: "cat336",
    name: "CAT 336",
    weight: "3.4t",
    arm: "6.8 m",
    forearm: "3.4 m",
    image: Cat336Image,
  },
  {
    id: "cat312",
    name: "CAT 312",
    weight: "3.4t",
    arm: "4.8 m",
    forearm: "2.4 m",
    image: Cat312Image,
  },
] as const;

const sizeTabs = [
    {
        name: "机械臂长度",
        key:  IUserDefaultSizeKey.roboticArmLen
    },
    {
        name: "连杆参数",
        key: IUserDefaultSizeKey.connectRodLen       
    },
    {
        name: "铲斗参数",
        key: IUserDefaultSizeKey.bucketParams
    },
    {
        name: "驾驶舱相对比例",
        key: IUserDefaultSizeKey.carbinProportion     
    },
] as const;

export const SizeSetting = () => {
  const { setSizeModel, setSaveAction, sizeModelSelected, sizeUserDefineInfo, setSizeUserDefineInfo } = useSettingStore();
  const [activeMode, setActiveMode] = useState<"machine" | "custom">("machine");
  const [activeSizeTab, setActiveSizeTab] = useState<(typeof sizeTabs)[number]>(sizeTabs[0]);


  useEffect(() => {
    sendToAndroid(IEventCode.GET_SIZE_INFO_SETTING_SAVED_SIGNAL);
    const unsubscribe = onAndroidMessage(IEventCode.GET_SIZE_INFO_SETTING_SAVED, (payload) => {
        if (payload) {
          const { mode, id } = payload as { mode: string, id: string };
          if (mode === "custom") {
            setSizeUserDefineInfo({
              ...payload
            })
          } else {
            setSizeModel(id);
          }
        }
    });

    return unsubscribe;
  }, [setSizeModel, setSizeUserDefineInfo]);

  const sendSavingSizeInfo = useCallback(() => {
      if (activeMode === 'custom') {
        sendToAndroid(IEventCode.SIZE_INFO_SETTING_SAVED_SIGNAL, {
          mode: activeMode,
          id: "user_define",
          ...sizeUserDefineInfo,
        });
      } else {
        if (!sizeModelSelected)
          return CommonToast.error("未选择机型，设置将不会生效");
            sendToAndroid(IEventCode.SIZE_INFO_SETTING_SAVED_SIGNAL, {
              mode: activeMode,
              id: sizeModelSelected,
            });
      }

      CommonToast.success("配置已更新");
  }, [activeMode, sizeModelSelected, sizeUserDefineInfo]);

  
  useLayoutEffect(() => {
    setSaveAction(sendSavingSizeInfo);

    return () => {
      setSaveAction(() => {});
    };
  }, [sendSavingSizeInfo, setSaveAction]);
  
  return (
    <div>
      <div className="text-[40px] font-semibold leading-tight text-black">尺寸信息</div>
      <div className="mt-3 text-[24px] leading-none text-[#8C8F96]">
        <p>配置挖掘机机械臂几何尺寸</p>
      </div>

      <div className="mt-11 flex items-center gap-16">
        <button
          className={[
            "relative pb-3 text-[28px] leading-none transition",
            activeMode === "machine" ? "text-black scale-105 font-semibold" : "text-[#101828]",
          ].join(" ")}
          type="button"
          onClick={() => setActiveMode("machine")}
        >
          选择机型
          {activeMode === "machine" && (
            <span className="absolute -bottom-1.5 left-1/2 h-1 w-12 -translate-x-1/2 rounded-full bg-[#005BFF]" />
          )}
        </button>
        <button
          className={[
            "relative pb-3 text-[28px] leading-none transition",
            activeMode === "custom" ? "text-black scale-105 font-semibold" : "text-[#101828]",
          ].join(" ")}
          type="button"
          onClick={() => {
            setActiveMode("custom")
            setSizeModel(undefined)
          }}
        >
          自定义尺寸
          {activeMode === "custom" && (
            <span className="absolute -bottom-1.5 left-1/2 h-1 w-12 -translate-x-1/2 rounded-full bg-[#005BFF]" />
          )}
        </button>
      </div>

      {activeMode === "machine" ? (
        <div className="mt-8 grid grid-cols-[repeat(3,minmax(0,350px))] gap-x-[74px] gap-y-8">
          {machineOptions.map((machine) => {
            const isSelected = sizeModelSelected === machine.id;

            return (
              <button
                key={machine.name}
                className={[
                  "relative flex h-[380px] flex-col rounded-2xl bg-white px-8 py-6 text-left shadow-[0_16px_34px_rgba(24,68,145,0.06)] transition",
                  isSelected ? "border-4 border-[#005BFF]" : "border-4 border-transparent",
                ].join(" ")}
                type="button"
                onClick={() => {
                  setSizeModel(machine.id);
                }}
              >
                {isSelected && (
                  <span className="absolute right-3 top-3 flex h-8 w-8 items-center justify-center rounded-full bg-[#005BFF] text-white">
                    <svg className="h-5 w-5" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true">
                      <path d="m5 10 3 3 7-7" />
                    </svg>
                  </span>
                )}

                <div className="flex h-[132px] items-center justify-center">
                  <img className="max-h-full max-w-full object-contain" src={machine.image} alt="" />
                </div>
                <div className="mt-4 text-[32px] font-semibold leading-none text-black">{machine.name}</div>
                <div className="mt-3 text-[22px] leading-none text-[#A7ACB5]">{machine.weight}</div>
                <div className="mt-4 h-px bg-[#EEF1F6]" />
                <dl className="mt-3 grid grid-cols-[1fr_auto] gap-y-2 text-[22px] leading-none text-[#B2B6BE]">
                  <dt>大臂</dt>
                  <dd>{machine.arm}</dd>
                  <dt>小臂</dt>
                  <dd>{machine.forearm}</dd>
                </dl>
              </button>
            );
          })}
        </div>
      ) : (
        <div>
          <div className="mt-8 flex flex-wrap items-center gap-5">
            {sizeTabs.map((tab) => (
              <button
                key={tab.key}
                className={[
                  "h-16 rounded-full px-8 text-[24px] font-semibold leading-none transition",
                  activeSizeTab.key === tab.key ? "bg-[#EAF2FF] text-[#005BFF]" : "bg-[#EEEEED] text-[#8A8D94]",
                ].join(" ")}
                type="button"
                onClick={() => setActiveSizeTab(tab)}
              >
                {tab.name}
              </button>
            ))}
          </div>

            <UserDefaultTab activeTab={activeSizeTab} />
        </div>
      )}
    </div>
  );
};
