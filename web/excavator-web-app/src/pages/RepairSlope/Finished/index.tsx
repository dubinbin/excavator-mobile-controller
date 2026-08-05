import { useNavigate } from "react-router-dom";
import { CommonHeader } from "@/components/CommonHeader";
import { StepBar } from "@/components/StepBar";
import { sendToAndroid } from "@/utils/bridge";
import { IEventCode, SlopeSelectedType, TEETH_TYPE_MAP } from "@/types";
import { useRepairSlopeStore } from "@/stores/modules/RepairSlopeStore";
import { closeWebView } from "@/utils/helper";
import { useMemo } from "react";
import {
  calculateRepairSlopeMeasurements,
  formatMeasurement,
} from "@/utils/repairSlope";

export const RepairSlopeFinished = () => {
  const navigation = useNavigate();
  const { abPointDistance, selectedAPointBucketTeeth, selectedBPointBucketTeeth, selectedCPointBucketTeeth, sectionParameter, pointA_Mode, pointB_Mode, PointAInfo, PointBInfo, PointCInfo, pointC_Mode, repairSlopeSelectedType } = useRepairSlopeStore();

  const slopeMeasurements = useMemo(
    () =>
      calculateRepairSlopeMeasurements(
        PointAInfo[pointA_Mode],
        PointBInfo[pointB_Mode],
        PointCInfo[pointC_Mode],
      ),
    [
      PointAInfo,
      PointBInfo,
      PointCInfo,
      pointA_Mode,
      pointB_Mode,
      pointC_Mode,
    ],
  );

    const taskInfoList = useMemo(() => {
      const abHeightDifference = slopeMeasurements
        ? `${formatMeasurement(slopeMeasurements.abHeightDifference, 3)} m`
        : "--";
      const verticalHeight = slopeMeasurements
        ? `${formatMeasurement(slopeMeasurements.verticalHeight, 3)} m`
        : "--";
      const horizontalDistance = slopeMeasurements
        ? `${formatMeasurement(slopeMeasurements.horizontalDistance, 3)} m`
        : "--";
      const slopeRatio = slopeMeasurements?.slopeRatio != null
        ? `1:${formatMeasurement(slopeMeasurements.slopeRatio, 2)}`
        : "--";
      const slopeAngle = slopeMeasurements
        ? `${formatMeasurement(slopeMeasurements.slopeAngle, 1)}°`
        : "--";

      return [
        ["辅助功能：", "修坡"],
        ["沟型：", repairSlopeSelectedType ===  SlopeSelectedType.bottom ? "下开口线" : "上开口线" ],
        ["A点参考点：", TEETH_TYPE_MAP[selectedAPointBucketTeeth].cn ],
        ["B点参考点：",TEETH_TYPE_MAP[selectedBPointBucketTeeth].cn ],
        ["C点参考点：", TEETH_TYPE_MAP[selectedCPointBucketTeeth].cn ],
        ["AB距离：", `${abPointDistance.toString()} m`],
        ["AB高差：", abHeightDifference],
        ["坡比：", slopeRatio],
        ["垂高：", verticalHeight],
        ["平距：", horizontalDistance],
        ["坡角：", slopeAngle],
      ];
    }, [
      abPointDistance,
      repairSlopeSelectedType,
      selectedAPointBucketTeeth,
      selectedBPointBucketTeeth,
      selectedCPointBucketTeeth,
      slopeMeasurements,
    ]);
  
  
  const sendAllDigWorkData = () => {
    navigation('')
    sendToAndroid(IEventCode.REPAIR_SLOPE_START_SIGNAL, {
      repairSlopeResult: {
        PointAInfo: PointAInfo[pointA_Mode],
        PointBInfo: PointBInfo[pointB_Mode],
        PointCInfo: PointCInfo[pointC_Mode], // C点参考点和B点参考点相同
        abPointDistance,
        repairSlopeSelectedType,
        abHeightDifference: slopeMeasurements?.abHeightDifference ?? null,
        slopeRatio: slopeMeasurements?.slopeRatio ?? null,
        verticalHeight: slopeMeasurements?.verticalHeight ?? null,
        horizontalDistance: slopeMeasurements?.horizontalDistance ?? null,
        slopeAngle: slopeMeasurements?.slopeAngle ?? null,
        sectionParameter: {
          ...sectionParameter
        }
      },
      MsgID: 0x30,
    });

    setTimeout(() => {
      closeWebView();
    }, 50);
  }

  return (
    <div className="min-h-screen bg-[#F6F8FD]  text-[#111] px-11 py-10">
      <CommonHeader title="作业前检查" />

      <main className="mx-auto mt-6 flex w-full min-h-[82vh] max-w-215 flex-col rounded-2xl bg-white px-11 py-11 text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] ">
        <p className="m-0 font-semibold leading-none text-black text-[32px]">
          任务信息
        </p>

        <div className="mt-9 min-h-0 overflow-y-auto pr-4 [scrollbar-color:#EDF3FF_transparent] scrollbar-thin">
          <div className="flex flex-col gap-y-6">
            {taskInfoList.map((item) => (
              <div className="flex items-center">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[#35C83A] text-xl font-semibold leading-none text-white">
                  ✓
                </span>
                <span className="ml-5 leading-none text-black text-[32px]">
                  {item}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 mt-12">
          <button
            className="h-20 rounded-xl border border-[#E7E7E7] bg-white text-2xl text-black shadow-[0_5px_18px_rgba(15,35,80,0.04)]"
            type="button"
            onClick={() => navigation("../step4")}
          >
            上一步
          </button>
          <button
            className="h-20 rounded-xl bg-[#006BFF] text-2xl font-semibold text-white shadow-[0_8px_20px_rgba(0,77,255,0.22)]"
            type="button"
            onClick={() => sendAllDigWorkData()}
          >
            确定, 开始作业
          </button>
        </div>
      </main>


        <StepBar length={5} activeStep={4} />
 
    </div>
  );
};
