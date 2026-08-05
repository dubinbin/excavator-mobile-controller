
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { CommonModal } from "@/components/CommonModal";
import { useSettingStore } from "@/stores/modules/SettingStore";
import { onAndroidMessage, sendToAndroid } from "@/utils/bridge";
import { IEventCode } from "@/types";

interface IGetConfigPayloadType {
    language: "zh-Hans" | "zh-Hant" | "en",
    brightness: number,
    videoStreamUrl: string;
}
export const CommonSetting = () => {
  const [language, setLanguage] = useState<"zh-Hans" | "zh-Hant" | "en">("zh-Hans");
  const [brightness, setBrightness] = useState(46);
  const [videoStreamUrl, setVideoStreamUrl] = useState("");
  const videoStreamInputRef = useRef<HTMLInputElement>(null);
  const { setSaveAction } = useSettingStore();

  const handleCheckUpdate = () => {
    CommonModal.show({
      title: "检查更新",
      content: (
        <div>
          <p>当前版本 V1.0.3 已是最新版本。</p>
        </div>
      ),
      okText: "确定",
      okAction: () => {},
      cancelText: "取消",
      cancelAction: () => {},
    });
  };

  const saveCommonConfig = useCallback(() => {
     sendToAndroid(IEventCode.SAVE_CONFIG_SIGNAL, {
       language,
       brightness,
       videoStreamUrl,
     })
  }, [brightness, language, videoStreamUrl])

  useEffect(() => {
    sendToAndroid(IEventCode.GET_SAVED_CONFIG_SINGAL);
    const unsubscribe = onAndroidMessage(IEventCode.GET_SAVED_CONFIG, (payload: IGetConfigPayloadType | undefined) => {
       if (payload) {
        setLanguage(payload.language);
        setBrightness(payload.brightness);
        setVideoStreamUrl(payload.videoStreamUrl);
       }
    });

    return unsubscribe;
  }, []);

    useLayoutEffect(() => {
      setSaveAction(saveCommonConfig);
  
      return () => {
        setSaveAction(() => {});
      };
    }, [saveCommonConfig, setSaveAction]);


    const modifyBrightToAndroid = () => {
      sendToAndroid(IEventCode.MODIFIY_BRIGHT_IMMEDIATELY_SIGNAL, {
        brightness
      })
    }

  const scrollVideoStreamInputIntoView = () => {
    window.setTimeout(() => {
      videoStreamInputRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "center",
      });
    }, 280);
  };

  return (
    <div>
      <div className="text-[40px] font-semibold leading-tight text-black">通用设置</div>
      <div className="mt-3 text-[24px] leading-none text-[#8C8F96]">
        <p>配置语言、显示与视频流等系统基础选项。</p>
      </div>

      <div className="mt-12">
        <section>
          <div className="text-[28px] font-semibold leading-none text-black">更改语言</div>
          <div className="mt-7 grid grid-cols-3 gap-12">
            {[
              { id: "zh-Hans", label: "简体中文" },
              { id: "zh-Hant", label: "繁體中文" },
              { id: "en", label: "English" },
            ].map((item) => {
              const isSelected = language === item.id;

              return (
                <button
                  key={item.id}
                  className={[
                    "h-16 rounded-full border text-[24px] font-semibold leading-none transition active:scale-[0.98]",
                    isSelected
                      ? "border-[#0B63FF] bg-[#EAF2FF] text-[#005BFF]"
                      : "border-[#C6CBD4] bg-transparent text-[#B9BDC6]",
                  ].join(" ")}
                  type="button"
                  onClick={() => setLanguage(item.id as typeof language)}
                >
                  {item.label}
                </button>
              );
            })}
          </div>
        </section>

        <section className="mt-16">
          <div className="text-[28px] font-semibold leading-none text-black">界面亮度调节</div>
          <div className="mt-8 grid grid-cols-[1fr_auto] items-end gap-8">
            <div>
              <div className="text-[24px] leading-none text-black">当前亮度</div>
              <div className="mt-2 text-[20px] leading-none text-[#8C8F96]">拖动滑块调节</div>
            </div>
            <div className="text-[42px] font-semibold leading-none text-[#005BFF]">{brightness}%</div>
          </div>
          <div className="relative">
            <input
              className="mt-5 h-3 w-full cursor-pointer appearance-none rounded-full accent-[#005BFF] [&::-webkit-slider-runnable-track]:h-3 [&::-webkit-slider-runnable-track]:rounded-full [&::-webkit-slider-thumb]:mt-[-6px] [&::-webkit-slider-thumb]:h-6 [&::-webkit-slider-thumb]:w-6 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-[#005BFF] [&::-webkit-slider-thumb]:shadow-[0_2px_8px_rgba(0,91,255,0.28)]"
              type="range"
              min="0"
              id="brightnessControl"
              max="100"
              value={brightness}
              style={{
                background: `linear-gradient(to right, #005BFF 0%, #005BFF ${brightness}%, #E2E9F8 ${brightness}%, #E2E9F8 100%)`,
              }}
              onChange={(event) => {
                setBrightness(Number(event.target.value));
                modifyBrightToAndroid();
              }}
            />

          </div>
        </section>

        <section className="mt-20">
          <div className="text-[28px] font-semibold leading-none text-black">视频流</div>
          <div className="mt-4 text-[18px] leading-none text-[#8C8F96]">RTSP 视频流地址</div>
          <input
            ref={videoStreamInputRef}
            className="mt-5 h-16 w-full scroll-mb-[360px] rounded-lg border border-[#E3E8F2] bg-white px-6 text-[22px] text-black outline-none transition placeholder:text-[#D3DAE6] focus:border-[#0B63FF]"
            type="text"
            value={videoStreamUrl}
            placeholder="请输入视频流地址"
            onFocus={scrollVideoStreamInputIntoView}
            onChange={(event) => setVideoStreamUrl(event.target.value)}
          />
        </section>

        <section className="mt-16">
          <div className="text-[28px] font-semibold leading-none text-black">系统信息</div>
          <div className="mt-5 grid grid-cols-[1fr_auto_auto] items-center gap-12">
            <div>
              <div className="text-[24px] leading-none text-black">当前版本</div>
            </div>
            <div className="text-[24px] leading-none text-black">V1.0.3</div>
            <button
              className="h-16 rounded-xl bg-[#0B63FF] px-9 text-[22px] font-medium text-white shadow-[0_10px_22px_rgba(0,91,255,0.24)] transition active:scale-[0.98]"
              type="button"
              onClick={handleCheckUpdate}
            >
              检查更新
            </button>
          </div>
        </section>
      </div>
    </div>
  );
};
