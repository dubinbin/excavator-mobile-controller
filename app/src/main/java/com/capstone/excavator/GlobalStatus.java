package com.capstone.excavator;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 应用进程内的全局状态。
 * <p>
 * 不写入 SharedPreferences 或其他持久化存储；需要共享的新状态可在此增加字段及对应的
 * getter/setter。
 */
public final class GlobalStatus {

    /** 一帧 IMU 数据中的实时角度快照，单位均为度。 */
    public static final class ImuAngles {
        public final float boomAngle;
        public final float stickAngle;
        public final float bucketAngle;
        public final float cabinPitchAngle;
        public final float cabinRollAngle;

        private ImuAngles(float boomAngle, float stickAngle, float bucketAngle,
                          float cabinPitchAngle, float cabinRollAngle) {
            this.boomAngle = boomAngle;
            this.stickAngle = stickAngle;
            this.bucketAngle = bucketAngle;
            this.cabinPitchAngle = cabinPitchAngle;
            this.cabinRollAngle = cabinRollAngle;
        }
    }

    /** 当前挖机的尺寸配置快照，尺寸字段与 Web 端同名字段一一对应。 */
    public static final class ExcavatorSizeConfig {
        private static final ExcavatorSizeConfig EMPTY = new ExcavatorSizeConfig(
                "", "",
                0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d,
                0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d);

        public final String mode;
        public final String id;
        public final double lb;
        public final double ls;
        public final double l2;
        public final double l3;
        public final double l4;
        public final double l5;
        public final double l6;
        public final double l7;
        public final double l8;
        public final double l10;
        public final double l11;
        public final double l12;
        public final double l13;
        public final double l14;
        public final double h1;
        public final double w;
        public final double h2;

        ExcavatorSizeConfig(String mode, String id,
                            double lb, double ls, double l2, double l3, double l4,
                            double l5, double l6, double l7, double l8, double l10,
                            double l11, double l12, double l13, double l14,
                            double h1, double w, double h2) {
            this.mode = mode;
            this.id = id;
            this.lb = lb;
            this.ls = ls;
            this.l2 = l2;
            this.l3 = l3;
            this.l4 = l4;
            this.l5 = l5;
            this.l6 = l6;
            this.l7 = l7;
            this.l8 = l8;
            this.l10 = l10;
            this.l11 = l11;
            this.l12 = l12;
            this.l13 = l13;
            this.l14 = l14;
            this.h1 = h1;
            this.w = w;
            this.h2 = h2;
        }

        public boolean isConfigured() {
            return !id.isEmpty();
        }

        static ExcavatorSizeConfig empty() {
            return EMPTY;
        }
    }

    /** Web 设置页保存的三个 IMU 配置值快照。 */
    public static final class ImuSetting {
        private static final ImuSetting EMPTY = new ImuSetting("", "", "");

        public final String imu1;
        public final String imu2;
        public final String imu3;

        ImuSetting(String imu1, String imu2, String imu3) {
            this.imu1 = imu1;
            this.imu2 = imu2;
            this.imu3 = imu3;
        }

        public boolean isConfigured() {
            return !imu1.isEmpty() && !imu2.isEmpty() && !imu3.isEmpty();
        }

        static ImuSetting empty() {
            return EMPTY;
        }
    }

    public interface OnMotionModeChangeListener {
        void onMotionModeChanged(int motionMode);
    }

    /** 每次写入一帧新的 IMU 快照后触发。回调线程与数据写入线程一致。 */
    public interface OnImuAnglesChangeListener {
        void onImuAnglesChanged(ImuAngles angles);
    }

    private static final GlobalStatus INSTANCE = new GlobalStatus();

    /** 当前运动模式下标：0 停止，1 底盘，2 铲斗。 */
    private volatile int motionMode = MotionModeSegmentView.INDEX_STOP;
    private volatile ImuAngles imuAngles = new ImuAngles(0f, 0f, 0f, 0f, 0f);
    private volatile ExcavatorSizeConfig excavatorSizeConfig = ExcavatorSizeConfig.empty();
    private volatile ImuSetting imuSetting = ImuSetting.empty();
    private final CopyOnWriteArrayList<OnMotionModeChangeListener> motionModeListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnImuAnglesChangeListener> imuAnglesListeners =
            new CopyOnWriteArrayList<>();

    private GlobalStatus() {
    }

    public static GlobalStatus getInstance() {
        return INSTANCE;
    }

    public int getMotionMode() {
        return motionMode;
    }

    public void setMotionMode(int motionMode) {
        this.motionMode = motionMode;
        for (OnMotionModeChangeListener listener : motionModeListeners) {
            listener.onMotionModeChanged(motionMode);
        }
    }

    public void addMotionModeChangeListener(OnMotionModeChangeListener listener) {
        if (!motionModeListeners.contains(listener)) {
            motionModeListeners.add(listener);
        }
    }

    public void removeMotionModeChangeListener(OnMotionModeChangeListener listener) {
        motionModeListeners.remove(listener);
    }

    public void updateImuAngles(float boomAngle, float stickAngle, float bucketAngle,
                                float cabinPitchAngle, float cabinRollAngle) {
        ImuAngles snapshot = new ImuAngles(
                boomAngle,
                stickAngle,
                bucketAngle,
                cabinPitchAngle,
                cabinRollAngle);
        imuAngles = snapshot;
        for (OnImuAnglesChangeListener listener : imuAnglesListeners) {
            listener.onImuAnglesChanged(snapshot);
        }
    }

    public void addImuAnglesChangeListener(OnImuAnglesChangeListener listener) {
        if (listener != null && !imuAnglesListeners.contains(listener)) {
            imuAnglesListeners.add(listener);
        }
    }

    public void removeImuAnglesChangeListener(OnImuAnglesChangeListener listener) {
        imuAnglesListeners.remove(listener);
    }

    public ImuAngles getRunTimeImuData() {
        return imuAngles;
    }

    public void setExcavatorSizeConfig(ExcavatorSizeConfig config) {
        excavatorSizeConfig = config != null ? config : ExcavatorSizeConfig.empty();
    }

    public ExcavatorSizeConfig getExcavatorSizeConfig() {
        return excavatorSizeConfig;
    }

    public void setImuSetting(ImuSetting setting) {
        imuSetting = setting != null ? setting : ImuSetting.empty();
    }

    public ImuSetting getImuSetting() {
        return imuSetting;
    }

    public ImuAngles getImuAngles() {
        return imuAngles;
    }

    public float getBoomAngle() {
        return imuAngles.boomAngle;
    }

    public float getStickAngle() {
        return imuAngles.stickAngle;
    }

    public float getBucketAngle() {
        return imuAngles.bucketAngle;
    }

    public float getCabinPitchAngle() {
        return imuAngles.cabinPitchAngle;
    }

    public float getCabinRollAngle() {
        return imuAngles.cabinRollAngle;
    }
}
