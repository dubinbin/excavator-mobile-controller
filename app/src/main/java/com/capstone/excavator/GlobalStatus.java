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
