import { useNavigate } from "react-router-dom";
import { CommonHeader } from "@/components/CommonHeader";
import { StepBar } from "@/components/StepBar";
import { useLevelingStore } from "@/stores/modules/LevelingStore";
import { useSystemStatusStore } from "@/stores/modules/SystemStore";
import { useMemo } from "react";
import { CorrectCheckIcon, ErrorCheckIcon } from "@/components/CommonIcon";
import { sendToAndroid } from "@/utils/bridge";
import { IEventCode } from "@/types";
import { closeWebView } from "@/utils/helper";

export const LevelingFinished = () => {
  const navigation = useNavigate();
  const { targetAltitude, digSize, bucketPos, currentFixationMode } = useLevelingStore();
  const allLevelTaskState = useLevelingStore();
  const { RTK_STATUS, IMU_STATUS } = useSystemStatusStore();

  const renderBucketPost = useMemo(() => {
    switch(bucketPos) {
      case "LEFT":
        return "左斗尖";
      case "MIDDLE":
        return "中斗尖";
      default:
        return "右斗尖";
    }
  }, [bucketPos])

  // 把当前用到的参数打包
  const sendAllLevelingData = () => {
    const result: Record<string, number | string> = {};
    for (const entry in allLevelTaskState) {
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-expect-error
      if (typeof allLevelTaskState[entry as unknown] !== 'function') {
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-expect-error
          result[entry] = allLevelTaskState[entry]
      }
    }

  // 把任务数据发送给安卓客户端
    sendToAndroid(IEventCode.LEVEL_TASK_START_SIGNAL, {
      levelTaskResult: result,
      MsgID: 0x11,
    })

    setTimeout(() => {
      closeWebView();
    }, 50);
  }

  return (
    <div className="min-h-screen bg-[#F6F8FD] text-[#111] px-11 py-10">
      <CommonHeader title="作业前检查" />

      <main className="mx-auto min-h-[81vh] mt-6 flex w-full max-w-215 flex-col rounded-2xl bg-white px-11 py-11 text-left shadow-[0_8px_30px_rgba(24,68,145,0.06)] ">
        <p className="m-0 font-semibold leading-none text-black text-[32px]">
          任务信息
        </p>
    
        <div className="mt-12 min-h-[54vh]  overflow-y-auto pr-4 [scrollbar-color:#EDF3FF_transparent] [scrollbar-width:thin]">
          <div className="flex flex-col gap-y-6">

              <div className="flex items-center">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[#35C83A] text-xl font-semibold leading-none text-white">
                  ✓
                </span>
                <span className="ml-5 leading-none text-black text-[32px]">
                  辅助功能: 找平
                </span>
              </div>


              <div className="flex items-center">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[#35C83A] text-xl font-semibold leading-none text-white">
                  ✓
                </span>

                <span className="ml-5 leading-none text-black text-[32px]">
                  参考点: {renderBucketPost}
                </span>
              </div>


              <div className="flex items-center">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[#35C83A] text-xl font-semibold leading-none text-white">
                  ✓
                </span>
                <span className="ml-5 leading-none text-black text-[32px]">
                  目标方式: {currentFixationMode === 'ALTITUDE' ? "高度定点" : "坐标定点"}
                </span>
              </div>

              <div className="flex flex-row gap-x-10">
                <div className="flex items-center">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[#35C83A] text-xl font-semibold leading-none text-white">
                    ✓
                  </span>
                  <span className="ml-5 leading-none text-black text-[32px]">
                    目标高度：<i className="not-italic">{targetAltitude} m</i>
                  </span>
                </div>

                <div className="flex items-center">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[#35C83A] text-xl font-semibold leading-none text-white">
                    ✓
                  </span>
                  <span className="ml-5 leading-none text-black text-[32px]">
                    填挖量:<i className="not-italic">  { Number(digSize) > 0 ? `+${digSize}`: `-${digSize}`} m</i>
                  </span>
                </div>
            </div>
            
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
            onClick={() => navigation("../step1")}
          >
            上一步
          </button>
          <button
            className="h-20 rounded-xl bg-[#006BFF] text-2xl font-semibold text-white shadow-[0_8px_20px_rgba(0,77,255,0.22)]"
            type="button"
            style={{
              background:( IMU_STATUS !== 'NORMAL' || RTK_STATUS !== 'CONNECTED') ? "#939393" : "#006BFF"
            }}
            onClick={() => {
              // if (IMU_STATUS !== 'NORMAL' || RTK_STATUS !== 'CONNECTED') {
              //   return;
              // }
              sendAllLevelingData();
            }}
          >
            确定, 开始作业
          </button>
        </div>
      </main>

 
        <StepBar length={2} activeStep={1} />

    </div>
  );
};
