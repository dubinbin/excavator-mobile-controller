import { create } from "zustand";

export const lableMap = [
    'Lb',
    'Ls',
    'L2',
    'L3',
    'L4',
    'L5',
    'L6',
    'L7',
    'L8',
    'L10',
    'L11',
    'L12',
    'L13',
    'L14',
    'H1',
    'W',
    'H2'
]

interface SettingState {
    rtspUrl: string;
    currentLanguage: "EN" | "CN" | "CN_HK",
    currentVersion: string,
    luminance: number,
    saveAction: () => void | null;
    setSaveAction: (fn: () => void) => void;
    setSizeUserDefineInfo: (sizeInfo: Record<string, string>) => void;
    sizeUserDefineInfo: Record<string, string>
    setSizeModel: (id: string|undefined) => void;
    sizeModelSelected: string | undefined;
    imuSetting: Record<string, string>;
    setImuSetting: (imus: Record<string, string>) => void;
}

export const useSettingStore = create<SettingState>((set) => ({
    rtspUrl: '',
    currentLanguage: "CN",
    luminance: 45,
    currentVersion: "1.0.0",
    saveAction: () => {},
    setCurrentLuminance: (luminance: number) => set(() => ({
        luminance: luminance
    })),
    setSaveAction: (fn: () => void) => set(() => ({
        saveAction: fn
    })),
    sizeUserDefineInfo: lableMap.reduce((prev, cur) => {
        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
        // @ts-expect-error
        prev[cur] = '0';
        return prev;
    }, {}),
    setSizeUserDefineInfo: (sizeInfo: Record<string, string>) => set((state) => ({
        sizeUserDefineInfo: {
            ...state.sizeUserDefineInfo,
            ...sizeInfo,
        },
    })),
    setSizeModel: (id: string | undefined) => set(() => ({
        sizeModelSelected: id
    })),
    sizeModelSelected: undefined,
    imuSetting: {
        imu1: '0',
        imu2: '0',
        imu3: '0',
    },
    setImuSetting: (imus: Record<string, string>) => set((state) => ({
       imuSetting: {
        ...state.imuSetting,
        ...imus
       }
    })),
}))
