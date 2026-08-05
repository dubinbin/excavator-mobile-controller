import { useCallback, useEffect, useRef, useState } from "react";
import { IEventCode, type IMEASUREMENT_POINT_RECEIVE_PAYLOAD } from "@/types";
import { onAndroidMessage, sendToAndroid } from "@/utils/bridge";

export const useMeasurementResult = () => {
  const pendingRef = useRef(false);
  const [measureResult, setMeasureResult] =
    useState<IMEASUREMENT_POINT_RECEIVE_PAYLOAD | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    const unsubscribe = onAndroidMessage(
      IEventCode.MEASUREMENT_POINT_RECEIVE,
      (payload) => {
        if (!pendingRef.current) return;
        console.error("Received measurement point:", payload);
        const result = payload as IMEASUREMENT_POINT_RECEIVE_PAYLOAD;
        pendingRef.current = false;
        setPending(false);
        setMeasureResult(result);
      },
    );

    return unsubscribe;
  }, []);

  const requestMeasurementPoint = useCallback((payload: {
    FeatureID: number;
    PointID: number;
    PointMode: number;
  }) => {
    pendingRef.current = true;
    setPending(true);
    sendToAndroid(IEventCode.MEASUREMENT_POINT_SIGNAL, payload);
  }, []);

  return {
    measureResult,
    pending,
    requestMeasurementPoint,
  };
};