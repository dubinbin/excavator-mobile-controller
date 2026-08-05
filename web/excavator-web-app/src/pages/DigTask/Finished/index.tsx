import { useNavigate } from "react-router-dom";
import { CommonHeader } from "@/components/CommonHeader";
import { StepBar } from "@/components/StepBar";
import { useSystemStatusStore } from "@/stores/modules/SystemStore";
import { CorrectCheckIcon, ErrorCheckIcon } from "@/components/CommonIcon";
import { useMemo } from "react";
import { useDigTaskState } from "@/stores/modules/DigTaskStore";
import { DigSelectedType, IEventCode, TEETH_TYPE_MAP } from "@/types";
import { sendToAndroid } from "@/utils/bridge";
import { closeWebView } from "@/utils/helper";

export const DigTaskFinished = () => {
  const navigation = useNavigate();
  const { RTK_STATUS, IMU_STATUS } = useSystemStatusStore();
  const { selectedAPointBucketTeeth, selectedBPointBucketTeeth, digSelectedType, abPointDistance, sectionParameter, pointA_Mode, pointB_Mode, PointAInfo, PointBInfo } = useDigTaskState();

  const taskInfoList = useMemo(() => {
    return [
      ["辅助功能：", "挖沟"],
      ["沟型：", digSelectedType ===  DigSelectedType.square ? "方形沟" : "梯形沟" ],
      ["A点参考点：",  TEETH_TYPE_MAP[selectedAPointBucketTeeth].cn ],
      ["B点参考点：",  TEETH_TYPE_MAP[selectedBPointBucketTeeth].cn ],
      ["AB距离：", `${abPointDistance.toString()} m`],
      ["沟深：", `${sectionParameter.H_Height.toString()} m`],
      ["左宽：", `${sectionParameter.L_Width.toString()} m`],
      ["右宽：",  `${sectionParameter.R_Width.toString()} m`],
      ["上口宽",  `${sectionParameter.W_Width.toString()} m`],
    ];
  }, [abPointDistance, digSelectedType, sectionParameter, selectedAPointBucketTeeth, selectedBPointBucketTeeth]);


  const sendAllDigWorkData = () => {
    navigation('')
    // 把任务数据发送给安卓客户端
    console.error({
        PointAInfo: PointAInfo[pointA_Mode],
        PointBInfo: PointBInfo[pointB_Mode],
        abPointDistance,
         ...sectionParameter
      })
    sendToAndroid(IEventCode.DIG_TASK_START_SIGNAL, {
      digTaskResult: {
        digSelectedType,
        PointAInfo: PointAInfo[pointA_Mode],
        PointBInfo: PointBInfo[pointB_Mode],
        abPointDistance,
         ...sectionParameter
      },
      MsgID: 0x20,
    })

    setTimeout(() => {
      closeWebView();
    }, 50);
  }

  return (
    <div className="min-h-screen bg-[#F6F8FD]  text-[#111] px-11 py-10">
      <CommonHeader title="作业前检查" />

      <main className="mx-auto mt-6 flex w-full max-w-215 flex-col rounded-2xl bg-white px-11 py-11 text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] ">
        <p className="m-0 font-semibold leading-none text-black text-[32px]">
          任务信息
        </p>

        <div className="mt-9 min-h-0 max-h-[55vh]  overflow-y-scroll pr-4 [scrollbar-color:#EDF3FF_transparent] [scrollbar-width:thin]">
          <div className="flex flex-col gap-y-6">
            {taskInfoList.map(([key, val]) => (
              <div key={key} className="flex items-center">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[#35C83A] text-xl font-semibold leading-none text-white">
                  ✓
                </span>
                <span className="ml-5 leading-none text-black text-[32px]">
                   {key}{val}
                </span>
              </div>
            ))}
          </div>

          <div className="border-b-[0.5px] border-[#E7E7E7] mt-16 mb-12" />

          {RTK_STATUS === 'CONNECTED' ?
          
            <div className="flex flex-col">
                <div className="flex items-center">
                  <CorrectCheckIcon />
                  <span className="ml-5 leading-none text-black text-[32px]">
                    RTK 状态：<i className="not-italic text-[#42C637]">已连接</i>
                  </span>
                </div>

                <div className="ml-12 mt-1 text-[#0C0C0C80] text-2xl">
                  <p>当前信号良好，符合作业要求</p>
                </div>
              </div>
            :

            <div className="flex flex-col">
                <div className="flex items-center">
                  <ErrorCheckIcon />
                  <span className="ml-5 leading-none text-black text-[32px]">
                    RTK 状态：<i className="not-italic text-[#FF0000]">数据异常</i>
                  </span>
                </div>

                <div className="ml-12 mt-1 text-[#0C0C0C80] text-2xl">
                  <p>当前信号断开，无法进行作业</p>
                </div>
              </div>
            }

          <div className="flex flex-col mt-5">
            {IMU_STATUS === 'NORMAL' ? 
              <div className="flex items-center">
               <CorrectCheckIcon />
                <span className="ml-5 leading-none text-black text-[32px]">
                  <span> IMU 状态：<i className="not-italic text-[#42C637]"> 数据正常</i></span>
                </span>
              </div>
            :
            <div className="flex items-center">
               <ErrorCheckIcon />
                <span className="ml-5 leading-none text-black text-[32px]">
                  <span> IMU 状态：<i className="not-italic text-[#FF0000]"> 数据异常</i></span>
                </span>
              </div>
            }

            <div className="ml-12 mt-1 text-[#0C0C0C80] text-2xl">
               <p>当前信号断开，无法进行作业</p>
            </div>
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
            onClick={sendAllDigWorkData}
          >
            确定, 开始作业
          </button>
        </div>
      </main>

      <StepBar length={5} activeStep={4} />
    </div>
  );
};
