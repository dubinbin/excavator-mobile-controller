import { lazy, Suspense, useEffect } from "react";
import { Navigate, NavLink, Route, Routes, useNavigate } from "react-router-dom";
import ImuSettingIcon from "@/assets/setting/imu_setting_icon.png";
import SizeSettingIcon from "@/assets/setting/dimensions_setting_icon.png";
import JoyStickIcon from "@/assets/setting/joystick_setting_icon.png";
import CommonSettingIcon from "@/assets/setting/common_setting_icon.png";
import Logo from "@/assets/black_logo.png"
import { sendToAndroid } from "@/utils/bridge";
import { IEventCode } from "@/types";
import { useSettingStore } from "@/stores/modules/SettingStore";
import { CommonModal } from "@/components/CommonModal";

const ImuSetting = lazy(() => import("./ImuSetting").then(({ ImuSetting }) => ({ default: ImuSetting })));
const SizeSetting = lazy(() => import("./SizeInfoSetting").then(({ SizeSetting }) => ({ default: SizeSetting })));
const JoyStickSetting = lazy(() => import("./JoyStickSetting").then(({ JoyStickSetting }) => ({ default: JoyStickSetting })));
const CommonSetting = lazy(() => import("./CommonSetting").then(({ CommonSetting }) => ({ default: CommonSetting })));

const menuItems = [
  {
    path: "imu_setting",
    title: "IMU 安装角度",
    subtitle: "偏差校准",
    icon: ImuSettingIcon,
  },
  {
    path: "size_setting",
    title: "尺寸信息",
    subtitle: "机械臂参数",
    icon: SizeSettingIcon
  },
  {
    path: "joystick_setting",
    title: "摇杆通道映射",
    subtitle: "控制方向配置",
    icon: JoyStickIcon
  },
  {
    path: "common_setting",
    title: "通用设置",
    subtitle: "语言、显示与视频流",
    icon: CommonSettingIcon
  },
] as const;

export default function Settings() {
  const navigation = useNavigate();
  const { saveAction } = useSettingStore();

  const saveConfig = () => {
    saveAction();
  }

  useEffect(() => {
    setTimeout(() => {
      sendToAndroid(IEventCode.WEBVIEW_READY_SIGNAL, {
        page: "SETTING"
      });
    }, 50);
  }, [])


  const webviewClose = () => {
    const posted = sendToAndroid(IEventCode.CLOSE_WEBVIEW_SIGNAL, {
        reason: "header_back",
    });

    if (!posted) {
        // 非 WebView 环境下回到应用首页，避免造成路由逻辑混乱
        navigation('/', { replace: true });
    }
  }

  const resetToDefault = () => {
      CommonModal.show({
        title: "重置为默认值",
        content: (
          <div>
            <p>是否将当值设置重置为默认值</p>
          </div>
        ),
        okText: "确定",
        okAction: () => {},
        cancelText: "取消",
        cancelAction: () => {},
      });
  }

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-[#F6F8FD] text-[#111827]">
      <header className="flex h-[88px] items-center justify-between border-b border-[#EEF1F7] bg-white px-6">
        <div className="flex items-center gap-6">
          <div className="flex items-center text-[40px] font-black leading-none tracking-[0.08em] text-black">
           <img className="w-60" src={Logo} alt="" />
          </div>
          <div className="text-2xl font-semibold text-[#5B6370]">参数设置</div>
        </div>

        <button
          className="inline-flex h-12 items-center gap-2 rounded-lg bg-[#006BFF] px-3 text-2xl font-medium text-white shadow-[0_8px_18px_rgba(0,107,255,0.22)] transition active:scale-[0.98]"
          type="button"
          onClick={saveConfig}
        >
          <svg className="h-7 w-7" viewBox="0 0 28 28" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
            <path d="M5 5h18v18H5z" />
            <path d="M9 5v7h10V5" />
            <path d="M9 18h10" />
          </svg>
          保存配置
        </button>
      </header>

      <div className="grid min-h-0 flex-1 grid-cols-[390px_1fr] overflow-hidden">
        <aside className="flex min-h-0 flex-col border-r border-[#EEF1F7] bg-white shadow-[0_12px_16px_rgb(205,205,195.50)]">
          <button
            className="ml-8 mt-6 inline-flex w-fit items-center gap-4 text-2xl font-medium text-[#006BFF]"
            type="button"
            onClick={webviewClose}
          >
            <svg className="h-8 w-8" viewBox="0 0 28 28" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden="true">
              <path d="m17 6-8 8 8 8" />
            </svg>
            返回
          </button>

          <nav className="mt-6">
            {menuItems.map((item) => (
              <NavLink
                key={item.path}
                className={({ isActive }) => [
                  "grid grid-cols-[68px_1fr] items-center gap-5 px-6 py-4 text-left transition",
                  isActive ? "bg-[#F1F5FF]" : "bg-white hover:bg-[#F7F9FE]",
                ].join(" ")}
                to={`/settings/${item.path}`}
              >
                {({ isActive }) => (
                  <>
                    <span
                      className={[
                        "flex h-[68px] w-[68px] items-center justify-center rounded-2xl",
                        isActive ? "bg-[#EEF4FF] text-[#006BFF]" : "bg-[#F6F8FC] text-[#006BFF]",
                      ].join(" ")}
                    >
                      <span className="block h-12 w-12 [&_svg]:h-full [&_svg]:w-full [&_svg]:fill-none [&_svg]:stroke-current [&_svg]:stroke-[2.2]">
                        <img src={item.icon} alt="" />
                      </span>
                    </span>
                    <span>
                      <span className="block text-[28px] font-normal leading-tight text-black">{item.title}</span>
                      <span className="mt-1 block text-[24px] leading-tight text-[#8E929A]">{item.subtitle}</span>
                    </span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <button
            className="mb-12 ml-12 mt-auto inline-flex w-fit items-center gap-4 text-2xl text-[#A5A9B2]"
            type="button"
            onClick={resetToDefault}
          >
            <svg className="h-8 w-8" viewBox="0 0 28 28" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <path d="M22 9a9 9 0 1 0 1 8" />
              <path d="M22 4v5h-5" />
            </svg>
            重置为默认值
          </button>
        </aside>

        <main className="min-h-0 overflow-y-auto overscroll-contain py-16 pl-20 pr-20 text-left [scrollbar-color:#D7E3F8_transparent] [scrollbar-width:thin]">
          <Suspense fallback={null}>
            <Routes>
              <Route index element={<Navigate to="imu_setting" replace />} />
              <Route path="imu_setting" element={<ImuSetting />} />
              <Route path="size_setting" element={<SizeSetting />} />
              <Route path="joystick_setting" element={<JoyStickSetting />} />
              <Route path="common_setting" element={<CommonSetting />} />
            </Routes>
          </Suspense>
        </main>
      </div>
    </div>
  )
}
