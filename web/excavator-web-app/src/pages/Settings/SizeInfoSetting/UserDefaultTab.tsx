import { NumericDisplayButton } from "@/components/NumericDisplayButton";
import { NumericKeyboard } from "@/components/NumericKeyboard";
import { IUserDefaultSizeKey } from "@/types";
import { useMemo, useState } from "react";
import P1 from "@/assets/setting/p1.png";
import P2 from "@/assets/setting/p2.png";
import P3 from "@/assets/setting/p3.png";
import P4 from "@/assets/setting/p4.png";
import { lableMap, useSettingStore } from "@/stores/modules/SettingStore";

interface IUserDefaultTabProps {
    activeTab: {
        name: string,
        key: IUserDefaultSizeKey
    }
}
export const UserDefaultTab = (props: IUserDefaultTabProps) => {
    const { activeTab } = props;
    const { key } = activeTab;
    const { sizeUserDefineInfo, setSizeUserDefineInfo } = useSettingStore();
    const manualValues = sizeUserDefineInfo;
    const [activeInput, setActiveInput] = useState<string | null>(null);
    const activeValue = activeInput ? manualValues[activeInput] ?? "" : "";
    const changeActiveValue = (value: string) => {
        if (!activeInput) return;
        setSizeUserDefineInfo({
            [activeInput]: value,
        });
    };

    const renderDifferentTab = useMemo(() => {
        switch (key) {
            case IUserDefaultSizeKey.roboticArmLen:
                return (
                    <div className="mt-7 flex flex-row items-start justify-between gap-11">
                        <div className="h-42 rounded-2xl bg-white px-8 pt-8 w-1/2 shadow-[0_16px_34px_rgba(24,68,145,0.06)]">
                            <div className="grid grid-cols-2 gap-8">

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">大臂长度 Lb </span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[0]] ?? 0}
                                    isActive={activeInput === lableMap[0]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[0])}
                                />
                                </span>
                            </label>

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">小臂（斗杆）长度 Ls</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[1]] ?? 0}
                                    isActive={activeInput === lableMap[1]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[1])}
                                />
                                </span>
                            </label>
                            </div>
                            
                        </div>

                        <div className="flex aspect-square w-1/2 items-center justify-center rounded-2xl bg-white p-12 shadow-[0_16px_34px_rgba(24,68,145,0.06)]">
                            <img className="h-full w-full object-contain" src={P1} alt="" />
                        </div>
                    </div>
                )

            case IUserDefaultSizeKey.connectRodLen:
                return (
                    <div className="mt-7 flex flex-row items-start justify-between gap-11">
                        <div className="rounded-2xl bg-white px-8 pt-8  w-1/2 shadow-[0_16px_34px_rgba(24,68,145,0.06)]">
                            <div className="grid grid-cols-2 gap-8 mb-6">

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L2</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[2]] ?? 0}
                                    isActive={activeInput === lableMap[2]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[2])}
                                />
                                </span>
                            </label>

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L3</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[3]] ?? 0}
                                    isActive={activeInput === lableMap[3]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[3])}
                                />
                                </span>
                            </label>
                            </div>


                        <div className="grid grid-cols-2 gap-8 mb-6">
                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L4</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[4]] ?? 0}
                                    isActive={activeInput === lableMap[4]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[4])}
                                />
                                </span>
                            </label>

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L5</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[5]] ?? 0}
                                    isActive={activeInput === lableMap[5]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[5])}
                                />
                                </span>
                            </label>
                            </div>



                            <div className="grid grid-cols-2 gap-8 mb-6">

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L6</span>
                                 <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[6]] ?? 0}
                                    isActive={activeInput === lableMap[6]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[6])}
                                />
                                </span>
                            </label>

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L7</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[7]] ?? 0}
                                    isActive={activeInput === lableMap[7]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[7])}
                                />
                                </span>
                            </label>
                            </div>
                            

                           <div className="grid grid-cols-2 gap-8 mb-6">

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L9</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[8]] ?? 0}
                                    isActive={activeInput === lableMap[8]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[8])}
                                />
                                </span>
                            </label>

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L10</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[9]] ?? 0}
                                    isActive={activeInput === lableMap[9]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[9])}
                                />
                                </span>
                            </label>
                            </div>
                        </div>

                        <div className="flex aspect-square w-1/2 items-center justify-center rounded-2xl bg-white p-12 shadow-[0_16px_34px_rgba(24,68,145,0.06)]">
                            <img className="h-full w-full object-contain" src={P2} alt="" />
                        </div>
                    </div>
                )
            case IUserDefaultSizeKey.bucketParams:
                return (
                    <div className="mt-7 flex flex-row items-start justify-between gap-11">
                        <div className="rounded-2xl bg-white px-8 pt-8  w-1/2  shadow-[0_16px_34px_rgba(24,68,145,0.06)] w-1/2">
                            <div className="grid grid-cols-2 gap-8 mb-6">

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L11</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[10]] ?? 0}
                                    isActive={activeInput === lableMap[10]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[10])}
                                />
                                </span>
                            </label>

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L12</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[11]] ?? 0}
                                    isActive={activeInput === lableMap[11]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[11])}
                                />
                                </span>
                            </label>
                            </div>

                            

                           <div className="grid grid-cols-2 gap-8 mb-6">

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L13</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[12]] ?? 0}
                                    isActive={activeInput === lableMap[12]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[12])}
                                />
                                </span>
                            </label>

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">连杆 L14</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[13]] ?? 0}
                                    isActive={activeInput === lableMap[13]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[13])}
                                />
                                </span>
                            </label>
                            </div>
                        </div>

                        <div className="flex aspect-square w-1/2 items-center justify-center rounded-2xl bg-white p-12 shadow-[0_16px_34px_rgba(24,68,145,0.06)]">
                            <img className="h-full w-full object-contain" src={P3} alt="" />
                        </div>
                    </div>
                )
            default:
                return (
                     <div className="mt-7 flex flex-row items-start justify-between gap-11">
                        <div className="rounded-2xl bg-white px-8 pt-8  w-1/2 shadow-[0_16px_34px_rgba(24,68,145,0.06)] w-1/2">
                            <div className="grid grid-cols-2 gap-8 mb-6">

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">底盘高度 H1</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[14]] ?? 0}
                                    isActive={activeInput === lableMap[14]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[14])}
                                />
                                </span>
                            </label>

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">履带宽度 W</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[15]] ?? 0}
                                    isActive={activeInput === lableMap[15]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[15])}
                                />
                                </span>
                            </label>
                            </div>

                            

                           <div className="grid grid-cols-2 gap-8 mb-6">

                            <label className="block">
                                <span className="block text-[24px] font-medium leading-none text-black">驾驶舱高度 H2</span>
                                <span className="mt-4 flex h-16 items-center rounded-xl border border-[#E4ECFF] bg-[#F7F9FE]">
                                <NumericDisplayButton
                                    value={manualValues[lableMap[16]] ?? 0}
                                    isActive={activeInput === lableMap[16]}
                                    className="grid h-[72px] w-full grid-cols-[1fr_auto] items-center rounded-md "
                                    valueClassName="min-w-0 pl-5 pr-4 text-[#97A4C2] text-2xl"
                                    unit={'m'}
                                    unitClassName="pr-5 text-[#A7AEC0] text-2xl"
                                    onClick={() => setActiveInput(lableMap[16])}
                                />
                                </span>
                            </label>

                            </div>
                        </div>

                        <div className="flex aspect-square w-1/2 items-center justify-center rounded-2xl bg-white p-12 shadow-[0_16px_34px_rgba(24,68,145,0.06)]">
                            <img className="h-full w-full object-contain" src={P4} alt="" />
                        </div>
                    </div>
                )
        }
    }, [activeInput, key, manualValues])

    return (
        <div className="relative">
            {renderDifferentTab}
            {activeInput && (
                <div className="absolute inset-0 z-10 flex items-center scale-80 justify-center scale-80 left-1/2">
                    <NumericKeyboard
                        value={activeValue}
                        onChange={changeActiveValue}
                        onClear={() => changeActiveValue("")}
                        onConfirm={() => setActiveInput(null)}
                    />
                </div>
            )}
    </div>
    )
}
