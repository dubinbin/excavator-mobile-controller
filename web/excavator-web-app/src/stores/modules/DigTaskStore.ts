import { COMMON_DIRECTION, DigSelectedType, type IMEASUREMENT_POINT_RECEIVE_PAYLOAD } from "@/types";
import { create } from "zustand";

type AutoPointInfo = IMEASUREMENT_POINT_RECEIVE_PAYLOAD;
type ManualPointInfo = {
    longitude: string;
    latitude: string;
    height: string;
};
type PointMode = "AUTO" | "MANUAL";
type PointInfoByMode = {
    AUTO: AutoPointInfo;
    MANUAL: ManualPointInfo;
};

export type SectionType = {
    L_Width: number,
    R_Width: number,
    W_Width: number,
    H_Height: number,
}

interface DigTaskState {
    digSelectedType: DigSelectedType;
    setDigSelectedType: (type: DigSelectedType) => void;
    selectedAPointBucketTeeth: COMMON_DIRECTION;
    selectedBPointBucketTeeth: COMMON_DIRECTION;
    PointAInfo: PointInfoByMode,
    PointBInfo: PointInfoByMode,
    pointA_Mode: PointMode,
    setPointAMode: (mode: PointMode) => void;
    pointB_Mode: PointMode,
    setPointBMode: (mode: PointMode) => void;
    setAPointInfo: {
        (info: AutoPointInfo, type: "AUTO"): void;
        (info: ManualPointInfo, type: "MANUAL"): void;
    };
    setBPointInfo: {
        (info: AutoPointInfo, type: "AUTO"): void;
        (info: ManualPointInfo, type: "MANUAL"): void;
    };
    setAPointBucketTeeth: (teeth: COMMON_DIRECTION) => void;
    setBPointBucketTeeth: (teeth: COMMON_DIRECTION) => void;
    abPointDistance: number;
    setABPointDistance: (num: number) => void;
    sectionParameter: SectionType,
    setSectionParameter: (params: Partial<SectionType>) => void,
}

export const useDigTaskState = create<DigTaskState>((set) => ({
    digSelectedType: DigSelectedType.square,
    setDigSelectedType: (type: DigSelectedType) => set(() => ({
       digSelectedType: type
    })),
    PointAInfo: {
        AUTO: {
            longitude: 0,
            latitude: 0,
            height: 0,
        },
        MANUAL: {
            longitude: "",
            latitude: "",
            height: "",
        },
    },
    PointBInfo: {
        AUTO: {
            longitude: 0,
            latitude: 0,
            height: 0,
        },
        MANUAL: {
            longitude: "",
            latitude: "",
            height: "",
        },
    },
    pointA_Mode: "AUTO",
    setPointAMode: (mode: PointMode) => set(() => ({
        pointA_Mode: mode,
    })),
    pointB_Mode: "AUTO",
    setPointBMode: (mode: PointMode) => set(() => ({
     pointB_Mode: mode,
    })),
    setAPointInfo: (info: AutoPointInfo | ManualPointInfo, type: PointMode) => set((state) => ({
        PointAInfo: {
            ...state.PointAInfo,
            [type]: {
                ...info,
            }
        }
    })),
    setBPointInfo: (info: AutoPointInfo | ManualPointInfo, type: PointMode) => set((state) => ({
        PointBInfo: {
           ...state.PointBInfo,
            [type]: {
                ...info,
            }
        }
    })),
    selectedAPointBucketTeeth: COMMON_DIRECTION.LEFT,
    setAPointBucketTeeth: (teeth: COMMON_DIRECTION) => set(() => ({
        selectedAPointBucketTeeth: teeth
    })),
    selectedBPointBucketTeeth: COMMON_DIRECTION.LEFT,
    setBPointBucketTeeth: (teeth: COMMON_DIRECTION) => set(() => ({
        selectedBPointBucketTeeth: teeth
    })),
    abPointDistance: 0,
    setABPointDistance: (num: number) => set(() => ({
        abPointDistance: num
    })),
    sectionParameter: {
        L_Width: 0,
        R_Width: 0,
        W_Width: 0,
        H_Height: 0, 
    },
    setSectionParameter: (params: Partial<SectionType>)=> set((state) => ({
        sectionParameter: {
            ...state.sectionParameter,
            ...params
        }
    })),
}))
