import { useSystemStatusStore } from "@/stores/modules/SystemStore";
import { IEventCode, type SystemStatus } from "@/types";
import { onAndroidMessage } from "@/utils/bridge";
import { lazy, Suspense, useEffect } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

const RepairSlopeFinished = lazy(() => import("./Finished").then(({ RepairSlopeFinished }) => ({ default: RepairSlopeFinished })));
const RepairSlopeStep1 = lazy(() => import("./Step1").then(({ RepairSlopeStep1 }) => ({ default: RepairSlopeStep1 })));
const RepairSlopeStep2 = lazy(() => import("./Step2").then(({ RepairSlopeStep2 }) => ({ default: RepairSlopeStep2 })));
const RepairSlopeStep3 = lazy(() => import("./Step3").then(({ RepairSlopeStep3 }) => ({ default: RepairSlopeStep3 })));
const RepairSlopeStep4 = lazy(() => import("./Step4").then(({ RepairSlopeStep4 }) => ({ default: RepairSlopeStep4 })));

export const RepairSlope = () => {

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
        <Route path="step1" element={<RepairSlopeStep1 />} />
        <Route path="step2" element={<RepairSlopeStep2 />} />
        <Route path="step3" element={<RepairSlopeStep3 />} />
        <Route path="step4" element={<RepairSlopeStep4 />} />
        <Route path="finished" element={<RepairSlopeFinished />} />
      </Routes>
    </Suspense>
  );
};
