import { useSystemStatusStore } from "@/stores/modules/SystemStore";
import { IEventCode, type SystemStatus } from "@/types";
import { onAndroidMessage } from "@/utils/bridge";
import { lazy, Suspense, useEffect } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

const LevelingFinished = lazy(() => import("./Finished").then(({ LevelingFinished }) => ({ default: LevelingFinished })));
const LevelingStep1 = lazy(() => import("./Step1").then(({ LevelingStep1 }) => ({ default: LevelingStep1 })));

export const LevelingTask = () => {

  const { set_imu_status, set_rtk_status } = useSystemStatusStore();
  useEffect(() => {
      const unsubscribe = onAndroidMessage(IEventCode.SYSTEM_STATUS_UPDATE, (payload) => {
          const { IMU_STATUS, RTK_STATUS } = payload as SystemStatus;
          set_imu_status(IMU_STATUS)
          set_rtk_status(RTK_STATUS)
      });
  
      return unsubscribe;
  }, [set_imu_status, set_rtk_status])

  return (
    <Suspense fallback={null}>
      <Routes>
        <Route index element={<Navigate to="step1" replace />} />
        <Route path="step1" element={<LevelingStep1 />} />
        <Route path="finished" element={<LevelingFinished />} />
      </Routes>
    </Suspense>
  );
};
