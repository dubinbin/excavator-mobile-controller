import { COMMON_DIRECTION, SlopeSelectedType, type IMEASUREMENT_POINT_RECEIVE_PAYLOAD } from "@/types";
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
    AB_Width: string,
    H_Width: string,
    L_Width: string,
    SLOPE_TYPE: "LEFT"|"RIGHT",
}

interface RepaireSlopeTaskState {
    repairSlopeSelectedType: SlopeSelectedType;
    setRepairSlopeSelectedType: (type: SlopeSelectedType) => void;
    selectedAPointBucketTeeth: COMMON_DIRECTION;
    selectedBPointBucketTeeth: COMMON_DIRECTION;
    selectedCPointBucketTeeth: COMMON_DIRECTION;    
    
    PointAInfo: PointInfoByMode,
    PointBInfo: PointInfoByMode,
    PointCInfo: PointInfoByMode,

    pointA_Mode: PointMode,
    setPointAMode: (mode: PointMode) => void;
    pointB_Mode: PointMode,
    setPointBMode: (mode: PointMode) => void;
    pointC_Mode: PointMode,
    setPointCMode: (mode: PointMode) => void;
    setAPointInfo: {
        (info: AutoPointInfo, type: "AUTO"): void;
        (info: ManualPointInfo, type: "MANUAL"): void;
    };
    setBPointInfo: {
        (info: AutoPointInfo, type: "AUTO"): void;
        (info: ManualPointInfo, type: "MANUAL"): void;
    };
    setCPointInfo: {
        (info: AutoPointInfo, type: "AUTO"): void;
        (info: ManualPointInfo, type: "MANUAL"): void;
    };
    setAPointBucketTeeth: (teeth: COMMON_DIRECTION) => void;
    setBPointBucketTeeth: (teeth: COMMON_DIRECTION) => void;
    setCPointBucketTeeth: (teeth: COMMON_DIRECTION) => void;
    abPointDistance: number;
    setABPointDistance: (num: number) => void;
    sectionParameter: SectionType,
    setSectionParameter: (params: Partial<SectionType>) => void,
}

export const useRepairSlopeStore = create<RepaireSlopeTaskState>((set) => ({
    repairSlopeSelectedType: SlopeSelectedType.top,
    setRepairSlopeSelectedType: (type: SlopeSelectedType) => set(() => ({
       repairSlopeSelectedType: type
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
    PointCInfo: {
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
    pointC_Mode: "AUTO",
    setPointCMode: (mode: PointMode) => set(() => ({
        pointC_Mode: mode,
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
    setCPointInfo: (info: AutoPointInfo | ManualPointInfo, type: PointMode) => set((state) => ({
        PointCInfo: {
           ...state.PointCInfo,
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
    selectedCPointBucketTeeth: COMMON_DIRECTION.LEFT,
    setCPointBucketTeeth: (teeth: COMMON_DIRECTION) => set(() => ({
        selectedCPointBucketTeeth: teeth
    })), 
    
    abPointDistance: 0,
    setABPointDistance: (num: number) => set(() => ({
        abPointDistance: num
    })),
    sectionParameter: {
        AB_Width: '0',
        H_Width: '0',
        L_Width: '0',
        SLOPE_TYPE: "RIGHT", 
    },
    setSectionParameter: (params: Partial<SectionType>)=> set((state) => ({
        sectionParameter: {
            ...state.sectionParameter,
            ...params
        }
    })),
}))
