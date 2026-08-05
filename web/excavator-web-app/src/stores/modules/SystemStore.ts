import { create } from "zustand";

interface SystemState {
    RTK_STATUS: "CONNECTED" | "OFFLINE"
    IMU_STATUS: "NORMAL" | "EXCEPTION",
    set_rtk_status: (stats: "CONNECTED" | "OFFLINE") => void;
    set_imu_status: (stats: "NORMAL" | "EXCEPTION") => void;
}

export const useSystemStatusStore = create<SystemState>((set) => ({
    RTK_STATUS: "OFFLINE",
    IMU_STATUS: "EXCEPTION",
    set_rtk_status: (status) => set(() => ({
        RTK_STATUS: status
    })),
    set_imu_status: (status) => set(() => ({
        IMU_STATUS: status
    })),
}))