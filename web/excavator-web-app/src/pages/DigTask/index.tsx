import { useSystemStatusStore } from "@/stores/modules/SystemStore";
import { IEventCode, type SystemStatus } from "@/types";
import { onAndroidMessage } from "@/utils/bridge";
import { lazy, Suspense, useEffect } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

const DigTaskFinished = lazy(() => import("./Finished").then(({ DigTaskFinished }) => ({ default: DigTaskFinished })));
const DigTaskStep1 = lazy(() => import("./Step1").then(({ DigTaskStep1 }) => ({ default: DigTaskStep1 })));
const DigTaskStep2 = lazy(() => import("./Step2").then(({ DigTaskStep2 }) => ({ default: DigTaskStep2 })));
const DigTaskStep3 = lazy(() => import("./Step3").then(({ DigTaskStep3 }) => ({ default: DigTaskStep3 })));
const DigTaskStep4 = lazy(() => import("./Step4").then(({ DigTaskStep4 }) => ({ default: DigTaskStep4 })));

export const DigTask = () => {
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
        <Route path="step1" element={<DigTaskStep1 />} />
        <Route path="step2" element={<DigTaskStep2 />} />
        <Route path="step3" element={<DigTaskStep3 />} />
        <Route path="step4" element={<DigTaskStep4 />} />
        <Route path="finished" element={<DigTaskFinished />} />
      </Routes>
    </Suspense>
  );
};
