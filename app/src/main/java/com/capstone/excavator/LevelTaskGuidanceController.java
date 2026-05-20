package com.capstone.excavator;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * 找平 / 挖沟作业引导 UI：左右竖条度量、左右速度方向卡、设计面快照与填挖量刷新。
 * MainActivity 在 LEVEL 或 DITCH 任务 RUNNING 时调用 {@link #onGuidanceRunningChanged}。
 */
public final class LevelTaskGuidanceController {

    private static final String TAG = "LevelGauge";

    /** 坡面任务样式边距（dp） */
    private static final float MARGIN_SLOPE_TASK_DP = 80.44f;
    /** 默认样式边距（dp） */
    private static final float MARGIN_DEFAULT_DP = 28.44f;

    /** 竖条槽数 */
    private static final int GAUGE_SLOTS_PER_HALF = 19;

    /** 度量单位 */
    private enum GaugeUnit { CM, DEG }

    /** 度量单位配置 */
    private static final class GaugeUnitConfig {
        /** 厘米每槽 */
        final float cmPerSlot;
        final float degPerSlot;

        GaugeUnitConfig(float cmPerSlot, float degPerSlot) {
            this.cmPerSlot = cmPerSlot;
            this.degPerSlot = degPerSlot;
        }

        float rangeFor(GaugeUnit u) {
            return GAUGE_SLOTS_PER_HALF * (u == GaugeUnit.CM ? cmPerSlot : degPerSlot);
        }
    }

    private final GaugeUnitConfig gaugeUnitConfig = new GaugeUnitConfig(3f, 3f);
    private GaugeUnit currentGaugeUnit = GaugeUnit.CM;

    private VerticalSpectrumGaugeView leftActivityGauge;
    private VerticalSpectrumGaugeView rightActivityGauge;
    private SpeedDirectionIndicatorView leftSpeedIndicator;
    private SpeedDirectionIndicatorView rightSpeedIndicator;

    private ImuAngleConverter.Config imuAngleConfig;
    /** 斗杆角度 */
    private float boomAngleDeg;
    /** 铲斗角度 */
    private float stickAngleDeg;
    /** 铲斗角度 */
    private float bucketAngleDeg;

    /** 设计面基准值 */
    private Double designZLocal;
    /** 作业运行状态前一次值 */
    private boolean guidanceRunningPrev;
    /** 设计面快照等待标志 */
    private boolean pendingDesignSnapshot;
    /** 当前作业类型 */
    private TaskTypeState.Type activeGuidanceTask = TaskTypeState.Type.NONE;

    /** 速度方向卡阈值（厘米） */
    private static final float GAUGE_DIR_THRESHOLD_CM = 0.5f;
    /** 找平度量条最大值（厘米） */
    private static final float LEVEL_DEPTH_CLAMP_CM = 120f;
    /** 找平度量条平滑因子 */
    private static final float LEVEL_DEPTH_EMA_ALPHA = 0.35f;
    /** 找平度量条平滑值 */
    private float smoothedLevelDepthCm;
    /** 找平度量条平滑初始化标志 */
    private boolean levelDepthSmoothingInitialized;

    /** 视图绑定标志 */
    private boolean viewsBound;

    /** 设置 IMU 角度配置 */
    public void setImuAngleConfig(ImuAngleConverter.Config config) {
        imuAngleConfig = config;
    }

    /** 设置 IMU 角度 */
    public void setImuAngles(float boomDeg, float stickDeg, float bucketDeg) {
        boomAngleDeg = boomDeg;
        stickAngleDeg = stickDeg;
        bucketAngleDeg = bucketDeg;
    }

    /** 绑定速度方向卡、活动竖条（在 Activity {@code setContentView} 之后调用一次）。 */
    public void bind(Activity activity) {
        leftSpeedIndicator = activity.findViewById(R.id.leftSpeedIndicator);
        rightSpeedIndicator = activity.findViewById(R.id.rightSpeedIndicator);
        if (leftActivityGauge == null) {
            leftActivityGauge = activity.findViewById(R.id.leftActivityGauge);
        }
        if (rightActivityGauge == null) {
            rightActivityGauge = activity.findViewById(R.id.rightActivityGauge);
        }
        applyGaugeUnitToViews();
        viewsBound = true;
    }

    /**
     * 任意作业激活时：速度方向卡水平边距与显隐（坡面任务边距更大）。
     */
    public void applySpeedIndicatorOverlay(boolean slopeTask, boolean taskActive) {
        float marginDp = slopeTask ? MARGIN_SLOPE_TASK_DP : MARGIN_DEFAULT_DP;
        setStartMarginDp(leftSpeedIndicator, marginDp);
        setEndMarginDp(rightSpeedIndicator, marginDp);
        setVisible(leftSpeedIndicator, taskActive);
        setVisible(rightSpeedIndicator, taskActive);
    }

    /**
     * 找平或挖沟作业 RUNNING 边沿：进入时快照设计面并刷新；退出时清零。
     */
    public void onGuidanceRunningChanged(
            boolean guidanceRunning,
            /** 当前作业类型 */
            TaskTypeState.Type taskType,
            /** 是否使用真实数据 */
            boolean useRealData) {
        if (guidanceRunning && !guidanceRunningPrev) {
            activeGuidanceTask = taskType;
            setGaugeUnit(GaugeUnit.CM);
            resetLevelDepthSmoothing();
            designZLocal = null;
            // 推迟到首次 setImuAngles 之后快照，避免任务刚激活时角度仍为 0°
            pendingDesignSnapshot = true;
            refresh(useRealData);
        } else if (!guidanceRunning && guidanceRunningPrev) {
            activeGuidanceTask = TaskTypeState.Type.NONE;
            designZLocal = null;
            pendingDesignSnapshot = false;
            resetLevelDepthSmoothing();
            if (leftActivityGauge != null) {
                leftActivityGauge.setValue(0f);
            }
            if (rightActivityGauge != null) {
                rightActivityGauge.setValue(0f);
            }

        
            applySpeedIndicators(0f);
        }
        guidanceRunningPrev = guidanceRunning;
    }

    /** @deprecated 请用 {@link #onGuidanceRunningChanged(boolean, TaskTypeState.Type, boolean)} */
    public void onLevelRunningChanged(boolean levelRunning, boolean useRealData) {
        onGuidanceRunningChanged(
                levelRunning,
                levelRunning ? TaskTypeState.Type.LEVEL : TaskTypeState.Type.NONE,
                useRealData);
    }

    /**
     * IMU/角度更新后调用：必要时补设计面快照，并刷新度量条与速度方向卡。
     */
    public void onImuUpdate(boolean useRealData) {
        tryCaptureDesignSnapshot(useRealData);
        refresh(useRealData);
    }

    /** 在 IMU 角度已写入后建立设计面基准（与实时斗尖 Z 同一套角度）。 */
    private void tryCaptureDesignSnapshot(boolean useRealData) {
        if (!guidanceRunningPrev || !pendingDesignSnapshot || !useRealData) {
            return;
        }
        if (snapshotDesignSurface(useRealData)) {
            pendingDesignSnapshot = false;
            Log.d(TAG, "design snapshot captured after first IMU frame");
        }
    }

    /** @return {@code true} 已成功写入 {@link #designZLocal} */
    private boolean snapshotDesignSurface(boolean useRealData) {
        if (!useRealData) {
            designZLocal = null;
            return false;
        }
        double zTipNow = currentBucketTipZ();
        if (Double.isNaN(zTipNow)) {
            designZLocal = null;
            return false;
        }
        if (activeGuidanceTask == TaskTypeState.Type.DITCH) {
            snapshotDitchDesignSurface(zTipNow);
            return designZLocal != null;
        }
        if (activeGuidanceTask == TaskTypeState.Type.LEVEL) {
            snapshotLevelDesignSurface(zTipNow);
            return designZLocal != null;
        }
        designZLocal = null;
        return false;
    }

    private void snapshotLevelDesignSurface(double zTipNow) {
        double offsetM;
        if (LevelTaskState.hasAcceptedTargetHeight() && LevelTaskState.hasSurveyHeight()) {
            offsetM = LevelTaskState.getAcceptedTargetHeightM() - LevelTaskState.getSurveyHeightM();
        } else if (LevelTaskState.hasNumericValues()) {
            offsetM = LevelTaskState.getTargetHeightM();
        } else {
            designZLocal = null;
            return;
        }
        if (Double.isNaN(offsetM)) {
            designZLocal = null;
            return;
        }
        designZLocal = zTipNow - offsetM;
        Log.d(TAG, "level snapshot z_design=" + designZLocal
                + " (z_tip=" + zTipNow + ", offsetM=" + offsetM + ")");
    }

    private void snapshotDitchDesignSurface(double zTipNow) {
        if (!DitchTaskState.hasGuidanceDesignData()) {
            designZLocal = null;
            return;
        }
        double offsetM = DitchTaskState.getGuidanceFillOffsetM();
        if (Double.isNaN(offsetM)) {
            designZLocal = null;
            return;
        }
        designZLocal = zTipNow - offsetM;
        Log.d(TAG, "ditch snapshot z_design=" + designZLocal
                + " (z_tip=" + zTipNow + ", offsetM=" + offsetM
                + ", heightMode=" + DitchTaskState.isHeightMode() + ")");
    }

    private double currentBucketTipZ() {
        if (imuAngleConfig == null) {
            return Double.NaN;
        }
        ImuAngleConverter.Dimensions dim = imuAngleConfig.dimensions;
        if (dim == null || dim.boomLength <= 0 || dim.stickLength <= 0 || dim.bucketLength <= 0) {
            return Double.NaN;
        }
        ImuAngleConverter.ImuInstallationOffset off = imuAngleConfig.imuOffsets;
        float boomAbsDeg = boomAngleDeg + (float) (off != null ? off.boomImuOffsetDeg : 0.0);
        float stickAbsDeg = stickAngleDeg + (float) (off != null ? off.stickImuOffsetDeg : 0.0);
        float bucketAbsDeg = bucketAngleDeg + (float) (off != null ? off.bucketImuOffsetDeg : 0.0);
        return ArmForwardKinematics.bucketTipZ(
                boomAbsDeg,
                stickAbsDeg,
                bucketAbsDeg,
                ImuPreferences.lengthMmToMeters(dim.boomLength),
                ImuPreferences.lengthMmToMeters(dim.stickLength),
                ImuPreferences.lengthMmToMeters(dim.bucketLength));
    }

    /** 刷新度量条与速度方向卡 */
    private void refresh(boolean useRealData) {
        /** 视图绑定标志 */
        if (!viewsBound) {
            return;
        }
        if (leftActivityGauge == null && rightActivityGauge == null
                && leftSpeedIndicator == null && rightSpeedIndicator == null) {
            return;
        }
        tryCaptureDesignSnapshot(useRealData);

        /** 作业运行状态前一次值 */
        if (!guidanceRunningPrev || !useRealData) {
            resetLevelDepthSmoothing();
            zeroGaugesAndIndicators();
            return;
        }
        if (designZLocal == null) {
            if (leftActivityGauge != null) {
                leftActivityGauge.setValue(0f);
            }
            if (rightActivityGauge != null) {
                rightActivityGauge.setValue(0f);
            }
            applySpeedIndicators(0f);
            return;
        }
        /** 当前斗尖 Z 值 核心调用方法 */
        double zTipNow = currentBucketTipZ();
        if (Double.isNaN(zTipNow)) {
            return;
        }
        /** 计算当前斗尖 Z 值与设计面基准值的差值 */
        System.out.println("LevelTaskGuidanceController: refresh: zTipNow=" + zTipNow + ", designZLocal=" + designZLocal);
        float rawValue;
        if (currentGaugeUnit == GaugeUnit.CM) {
            rawValue = (float) ((zTipNow - designZLocal) * 100.0);
            rawValue = clampLevelDepthCm(rawValue);
        } else {
            rawValue = (float) (zTipNow - designZLocal);
        }
        float value = smoothLevelDepthCm(rawValue);
        if (leftActivityGauge != null) {
            leftActivityGauge.setValue(value);
        }
        if (rightActivityGauge != null) {
            rightActivityGauge.setValue(value);
        }
        applySpeedIndicators(value);
    }

    private void zeroGaugesAndIndicators() {
        if (leftActivityGauge != null) {
            leftActivityGauge.setValue(0f);
        }
        if (rightActivityGauge != null) {
            rightActivityGauge.setValue(0f);
        }
        applySpeedIndicators(0f);
    }

    private void resetLevelDepthSmoothing() {
        smoothedLevelDepthCm = 0f;
        levelDepthSmoothingInitialized = false;
    }

    private static float clampLevelDepthCm(float valueCm) {
        if (valueCm > LEVEL_DEPTH_CLAMP_CM) {
            return LEVEL_DEPTH_CLAMP_CM;
        }
        if (valueCm < -LEVEL_DEPTH_CLAMP_CM) {
            return -LEVEL_DEPTH_CLAMP_CM;
        }
        return valueCm;
    }

    private float smoothLevelDepthCm(float rawValueCm) {
        if (!levelDepthSmoothingInitialized) {
            smoothedLevelDepthCm = rawValueCm;
            levelDepthSmoothingInitialized = true;
            return rawValueCm;
        }
        smoothedLevelDepthCm += LEVEL_DEPTH_EMA_ALPHA * (rawValueCm - smoothedLevelDepthCm);
        return smoothedLevelDepthCm;
    }

    private void applyGaugeUnitToViews() {
        float range = gaugeUnitConfig.rangeFor(currentGaugeUnit);
        if (leftActivityGauge != null) {
            leftActivityGauge.setRangeMax(range);
            leftActivityGauge.setValue(0f);
        }
        if (rightActivityGauge != null) {
            rightActivityGauge.setRangeMax(range);
            rightActivityGauge.setValue(0f);
        }
    }

    private void setGaugeUnit(GaugeUnit unit) {
        if (unit == null || unit == currentGaugeUnit) {
            return;
        }
        currentGaugeUnit = unit;
        applyGaugeUnitToViews();
    }

    private void applySpeedIndicators(float valueCm) {
        int dir;
        if (valueCm > GAUGE_DIR_THRESHOLD_CM) {
            dir = SpeedDirectionIndicatorView.DIRECTION_DOWN_HIGHLIGHT;
        } else if (valueCm <= -GAUGE_DIR_THRESHOLD_CM) {
            dir = SpeedDirectionIndicatorView.DIRECTION_UP_HIGHLIGHT;
        } else {
            dir = SpeedDirectionIndicatorView.DIRECTION_NEUTRAL;
        }
        float mag = Math.abs(valueCm);
        if (leftSpeedIndicator != null) {
            leftSpeedIndicator.setSpeed(mag);
            leftSpeedIndicator.setDirection(dir);
        }
        if (rightSpeedIndicator != null) {
            rightSpeedIndicator.setSpeed(mag);
            rightSpeedIndicator.setDirection(dir);
        }
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static void setStartMarginDp(View view, float startMarginDp) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
        int startMarginPx = Math.round(startMarginDp * view.getResources().getDisplayMetrics().density);
        if (marginLayoutParams.getMarginStart() == startMarginPx) {
            return;
        }
        marginLayoutParams.setMarginStart(startMarginPx);
        view.setLayoutParams(marginLayoutParams);
    }

    private static void setEndMarginDp(View view, float endMarginDp) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
        int endMarginPx = Math.round(endMarginDp * view.getResources().getDisplayMetrics().density);
        if (marginLayoutParams.getMarginEnd() == endMarginPx) {
            return;
        }
        marginLayoutParams.setMarginEnd(endMarginPx);
        view.setLayoutParams(marginLayoutParams);
    }
}
