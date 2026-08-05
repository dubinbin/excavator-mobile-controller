

import LeftBucketActiveIcon from "@/assets/buckets/left-bucket-active.png";
import MiddleBucketActiveIcon from "@/assets/buckets/middle-bucket-active.png";
import RightBucketActiveIcon from "@/assets/buckets/right-bucket-active.png";
import LeftBucketUnActiveIcon from "@/assets/buckets/left-bucket-unactive.png";
import MiddleBucketUnActiveIcon from "@/assets/buckets/middle-bucket-unactive.png";
import RightBucketUnActiveIcon from "@/assets/buckets/right-bucket-unactive.png";

export enum IActiveTab {
    SELECTED_MODEL = 1,
    USER_DEFAULT_SIZE = 2
}

export enum IUserDefaultSizeKey {
    roboticArmLen = "roboticArmLen",
    connectRodLen = "connectRodLen",
    bucketParams = "bucketParams",
    carbinProportion = "carbinProportion"
}

export type IMEASUREMENT_POINT_RECEIVE_PAYLOAD = {
    height: number;
    longitude: number;
    latitude: number;
}

export enum DIG_POINT_KEYS {
    height = "height",
    longitude = "longitude",
    latitude = "latitude",
}

export enum SPECIAL_INPUT_KEY {
    abPointDistance = 'abPointDistance'  // AB距离
}

export enum REPAIR_SLOPE_POINT_KEYS {
    height = "height",
    longitude = "longitude",
    latitude = "latitude",
}

// 0 停止，1 底盘，2 铲斗
export enum CURRENT_STATUS_KEYS {
    STOP = 0,
    CHASSIS = 1,
    BUCKET = 2
}


export enum COMMON_DIRECTION  {
    LEFT = "LEFT",
    MIDDLE = "MIDDLE",
    RIGHT = "RIGHT",
}

export enum DigSelectedType {
    square = "square",
    trapezoid = "trapezoid",
}

export enum SlopeSelectedType {
    top = "top",
    bottom = "bottom",
}

export enum SectionEnumType {
    L_Width = "L_Width",
    R_Width = "R_Width",
    W_Width = "W_Width",
    H_Height = "H_Height", 
}

export enum SPECAIL_INPUT_TYPE_SLOPE {
    AB_Width = "AB_Width",
    H_Width = "H_Width",
    L_Width = "L_Width",
    SLOPE_TYPE = "SLOPE_TYPE"
}

export enum TARGET_SET_MODE {
    "HEIGHT_MODE" = 0,
    "COORDINATE_MODE" = 1,
}


// SIGNAL结尾是发给安卓的key，非SIGNAL结尾是安卓发回给webview的key
export enum IEventCode {
    SAVE_CONFIG_SIGNAL = "SAVE_CONFIG_SIGNAL",// 保存通用配置

    LEVEL_TASK_START_SIGNAL = "LEVEL_TASK_START", // 找平任务开始,发送参数
    DIG_TASK_START_SIGNAL = "DIG_TASK_START", // 挖沟任务开始，发送参数
    REPAIR_SLOPE_START_SIGNAL = "REPAIR_SLOPE_START", // 修坡任务开始

    GET_CURRENT_STATUS = "GET_CURRENT_STATUS", // 获取当前状态
    GET_CURRENT_STATUS_SIGNAL = "GET_CURRENT_STATUS_SIGNAL", // 获取当前状态信号

    GET_LEVEL_CALC_DIG_AMOUNT_SIGNAL = "GET_LEVEL_CALC_DIG_AMOUNT_SIGNAL", // 请求找平获取计算后的预览高度
    RECEIVE_LEVEL_CALC_DIG_AMOUNT = "RECEIVE_LEVEL_CALC_DIG_AMOUNT", // 获取安卓动态计算后预览的高度

    MEASUREMENT_POINT_SIGNAL = "MEASUREMENT_POINT_CALL", // 通用测点
    MEASUREMENT_POINT_RECEIVE = "MEASUREMENT_POINT_RECEIVE", // 等待回传测点结果
    JOYSTICK_MAPPING_SAVED_SIGNAL = "JOYSTICK_MAPPING_SAVED", //  设置 修改遥控通道映射

    SIZE_INFO_SETTING_SAVED_SIGNAL = "SIZE_INFO_SETTING_SAVED_SIGNAL", // 设置 尺寸信息保存修改
    GET_SIZE_INFO_SETTING_SAVED_SIGNAL = "GET_SIZE_INFO_SETTING_SAVED_SIGNAL", // 获取 尺寸信息配置
    GET_SIZE_INFO_SETTING_SAVED = "GET_SIZE_INFO_SETTING_SAVED", // 获取 尺寸信息配置onmessage


    IMU_INFO_SETTING_SAVED_SIGNAL = "IMU_INFO_SETTING_SAVED_SIGNAL", // 设置 imu保存修改
    GET_IMU_INFO_SETTING_SAVED_SIGNAL = "GET_IMU_INFO_SETTING_SAVED_SIGNAL", // 获取imu信息配置
    GET_IMU_INFO_SETTING_SAVED = "GET_IMU_INFO_SETTING_SAVED", // 获取imu信息配置onmessage

    WEBVIEW_READY_SIGNAL = "WEBVIEW_READY", // webview加载完毕
    CLOSE_WEBVIEW_SIGNAL = "CLOSE_WEBVIEW", // 关闭当前 WebView
    GET_JOYSTICK_MAPPING_SIGNAL = "JOYSTICK_MAPPING", // 获取joystick的默认排布
    GET_SAVED_CONFIG = "GET_SAVED_CONFIG",  // 获取已经获取的配置 SIGNAL
    GET_SAVED_CONFIG_SINGAL = "GET_SAVED_CONFIG_SINGAL", // 获取已经获取的配置 SIGNAL
    MODIFIY_BRIGHT_IMMEDIATELY_SIGNAL = "MODIFIY_BRIGHT_IMMEDIATELY", // 即时修改屏幕亮度

    SYSTEM_STATUS_UPDATE = "SYSTEM_STATUS_UPDATE" // IMU RTK等信息传递
}


export const TEETH_TYPE_MAP = {
   [COMMON_DIRECTION.LEFT]: {
     cn: "左斗尖",
     en: "",
     hk: ""
   },
   [COMMON_DIRECTION.MIDDLE]: {
     cn: "中斗尖",
     en: "",
     hk: ""
   },
    [COMMON_DIRECTION.RIGHT]: {
     cn: "右斗尖",
     en: "",
     hk: ""
   },
}

export const directionOptions = [
  {
    id: COMMON_DIRECTION.LEFT,
    title: "左斗尖",
    activeIcon: LeftBucketActiveIcon,
    unActiveIcon: LeftBucketUnActiveIcon,
  },
  {
    id: COMMON_DIRECTION.MIDDLE,
    title: "中斗尖",
    activeIcon: MiddleBucketActiveIcon,
    unActiveIcon: MiddleBucketUnActiveIcon,
  },
  {
    id: COMMON_DIRECTION.RIGHT,
    title: "右斗尖",
    activeIcon: RightBucketActiveIcon,
    unActiveIcon: RightBucketUnActiveIcon,
  },
] as const;

export interface SystemStatus {
    RTK_STATUS: "CONNECTED" | "OFFLINE"
    IMU_STATUS: "NORMAL" | "EXCEPTION",
}