import { create } from "zustand";

// const toNumber = (value: string) => {
//     const numberValue = Number(value);
//     return Number.isFinite(numberValue) ? numberValue : 0;
// };

// const formatNumber = (value: number) => Number(value.toFixed(6)).toString();

// const calcTargetAltitude = (currentReferencePoint: string, digSize: string) => (
//     formatNumber(toNumber(currentReferencePoint) + toNumber(digSize))
// );

interface LevelingState {
    bucketPos: "LEFT" | "MIDDLE" | "RIGHT",
    setBucketPos: (pos: "LEFT" | "MIDDLE" | "RIGHT") => void;
    currentReferencePoint: string; // 当前参考点
    setCurrentReferencePoint: (point: string) => void;
    targetAltitude: string; // 目标高度
    setTargetAltitude: (size: string) => void;
    digSize: string; // 填挖量
    setDigSize: (size: string) => void;
    currentLongitudeAndLatitude: string;
    targetLongitude: string;
    targetLatitude: string;
    setTargetLongitudeAndLatitude: (longitude: string | undefined, latitude: string | undefined) => void;
    currentFixationMode: "ALTITUDE" | "COORDINATE" // 定点方式
    setCurrentFixationMode: (mode: "ALTITUDE" | "COORDINATE") => void;
}

export const useLevelingStore = create<LevelingState>((set) => ({
    bucketPos: "LEFT",
    setBucketPos: (pos) => set(() => ({
       bucketPos: pos
    })),
    currentReferencePoint: '0',
    targetAltitude: '0',
    digSize: '0',
    setCurrentReferencePoint: (point) => set(() => ({
        currentReferencePoint: point,
    })),
    setTargetAltitude: (size) => set(() => ({
        targetAltitude: size
    })),
    setDigSize: (size) => set(() => ({
        digSize: size,
    })),
    currentLongitudeAndLatitude: '0,0',
    targetLongitude: '0',
    targetLatitude: '0',
    setTargetLongitudeAndLatitude: (longitude, latitude) => set((state) => ({
        targetLongitude: longitude !== undefined ? longitude : state.targetLongitude,
        targetLatitude:  latitude !== undefined ? latitude : state.targetLatitude,
    })),
    currentFixationMode: "ALTITUDE",
    setCurrentFixationMode: (mode) => set(() => ({
        currentFixationMode: mode
    })),
}))
