package com.capstone.excavator;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

/** 管理找平/挖沟光谱竖条、修坡垂距条和速度方向卡三组相互独立的引导 UI。 */
public final class TaskGuidanceController {

    /** 每侧 19 格，每格默认表示 5 cm。 */
    private static final float DEFAULT_ACTIVITY_GAUGE_RANGE_CM = 19f * 5f;

    /** 速度方向卡的中立区，避免接近零点时上下方向频繁跳动。 */
    private static final float SPEED_DIRECTION_DEAD_ZONE_CM = 0.5f;

    /** 修坡任务时速度方向卡的水平边距（dp）。 */
    private static final float MARGIN_SLOPE_TASK_DP = 80.44f;

    /** 找平 / 挖沟任务时速度方向卡的水平边距（dp）。 */
    private static final float MARGIN_DEFAULT_DP = 28.44f;

    /** 找平 / 挖沟使用的左右光谱竖条。 */
    private VerticalSpectrumGaugeView leftActivityGauge;
    private VerticalSpectrumGaugeView rightActivityGauge;

    /** 修坡使用的左右垂距条。 */
    private SingleBarGaugeView leftSlopeGauge;
    private SingleBarGaugeView rightSlopeGauge;

    /** 左右数值及方向卡。 */
    private SpeedDirectionIndicatorView leftSpeedIndicator;
    private SpeedDirectionIndicatorView rightSpeedIndicator;

    /** 在 Activity {@code setContentView} 之后绑定三组引导 View。 */
    public void bind(Activity activity) {
        leftActivityGauge = activity.findViewById(R.id.leftActivityGauge);
        rightActivityGauge = activity.findViewById(R.id.rightActivityGauge);

        VerticalActivityPanelView leftSlopePanel =
                activity.findViewById(R.id.verticalActivityPanelLeft);
        VerticalActivityPanelView rightSlopePanel =
                activity.findViewById(R.id.verticalActivityPanelRight);
        leftSlopeGauge = leftSlopePanel == null ? null : leftSlopePanel.getGauge();
        rightSlopeGauge = rightSlopePanel == null ? null : rightSlopePanel.getGauge();

        leftSpeedIndicator = activity.findViewById(R.id.leftSpeedIndicator);
        rightSpeedIndicator = activity.findViewById(R.id.rightSpeedIndicator);

        setActivityGaugeRangeCm(DEFAULT_ACTIVITY_GAUGE_RANGE_CM);
        clear();
    }

    /**
     * 更新左右 {@link VerticalSpectrumGaugeView}。
     *
     * @param leftDeviationCm 左侧有符号偏离量（cm）
     * @param rightDeviationCm 右侧有符号偏离量（cm）
     */
    public void updateActivityGaugeDeviations(
            float leftDeviationCm,
            float rightDeviationCm) {
        setActivityGaugeValue(leftActivityGauge, leftDeviationCm);
        setActivityGaugeValue(rightActivityGauge, rightDeviationCm);
    }

    /**
     * 只更新修坡任务左右 {@link SingleBarGaugeView}，不影响找平/挖沟竖条和速度方向卡。
     *
     * <p>输入单位为厘米；当前默认量程来自 XML 的 {@code sbgRangeMax=10}。</p>
     */
    public void updateSlopeGaugeDeviations(
            float leftDeviationCm,
            float rightDeviationCm) {
        setSlopeGaugeValue(leftSlopeGauge, leftDeviationCm);
        setSlopeGaugeValue(rightSlopeGauge, rightDeviationCm);
    }

    /**
     * 更新左右 {@link SpeedDirectionIndicatorView}。
     *
     * <p>每侧独立根据偏离量决定方向，并显示偏离量的绝对值：</p>
     * <ul>
     *     <li>大于中立区：高亮向下；</li>
     *     <li>小于中立区负值：高亮向上；</li>
     *     <li>位于中立区：方向中立。</li>
     * </ul>
     *
     * @param leftDeviationCm 左侧有符号偏离量（cm）
     * @param rightDeviationCm 右侧有符号偏离量（cm）
     */
    public void updateSpeedIndicatorDeviations(
            float leftDeviationCm,
            float rightDeviationCm) {
        setSpeedIndicatorValue(leftSpeedIndicator, leftDeviationCm);
        setSpeedIndicatorValue(rightSpeedIndicator, rightDeviationCm);
    }

    /** 设置左右光谱竖条的最大绝对量程（cm）。 */
    public void setActivityGaugeRangeCm(float maxAbsCm) {
        if (!isFinite(maxAbsCm) || maxAbsCm <= 0f) {
            return;
        }
        if (leftActivityGauge != null) {
            leftActivityGauge.setRangeMax(maxAbsCm);
        }
        if (rightActivityGauge != null) {
            rightActivityGauge.setRangeMax(maxAbsCm);
        }
    }

    /** 设置修坡左右垂距条的最大绝对量程。 */
    public void setSlopeGaugeRangeCm(float maxAbsCm) {
        if (!isFinite(maxAbsCm) || maxAbsCm <= 0f) {
            return;
        }
        if (leftSlopeGauge != null) {
            leftSlopeGauge.setRangeMax(maxAbsCm);
        }
        if (rightSlopeGauge != null) {
            rightSlopeGauge.setRangeMax(maxAbsCm);
        }
    }

    /** 清空左右光谱竖条，不影响速度方向卡。 */
    public void clearActivityGauges() {
        setActivityGaugeValue(leftActivityGauge, 0f);
        setActivityGaugeValue(rightActivityGauge, 0f);
    }

    /** 清空修坡左右垂距条，不影响另外两组引导 View。 */
    public void clearSlopeGauges() {
        setSlopeGaugeValue(leftSlopeGauge, 0f);
        setSlopeGaugeValue(rightSlopeGauge, 0f);
    }

    /** 清空左右速度方向卡，不影响光谱竖条。 */
    public void clearSpeedIndicators() {
        setSpeedIndicatorValue(leftSpeedIndicator, 0f);
        setSpeedIndicatorValue(rightSpeedIndicator, 0f);
    }

    /** 清空三组引导 View。 */
    public void clear() {
        clearActivityGauges();
        clearSlopeGauges();
        clearSpeedIndicators();
    }

    /**
     * 设置速度方向卡的显隐及布局边距。
     */
    public void applySpeedIndicatorOverlay(boolean slopeTask, boolean taskActive) {
        float marginDp = slopeTask ? MARGIN_SLOPE_TASK_DP : MARGIN_DEFAULT_DP;
        setStartMarginDp(leftSpeedIndicator, marginDp);
        setEndMarginDp(rightSpeedIndicator, marginDp);
        setVisible(leftSpeedIndicator, taskActive);
        setVisible(rightSpeedIndicator, taskActive);
    }

    private static void setActivityGaugeValue(
            VerticalSpectrumGaugeView gauge,
            float deviationCm) {
        if (gauge != null) {
            gauge.setValue(sanitizeDeviation(deviationCm));
        }
    }

    private static void setSlopeGaugeValue(
            SingleBarGaugeView gauge,
            float deviationCm) {
        if (gauge != null) {
            gauge.setValue(sanitizeDeviation(deviationCm));
        }
    }

    private static void setSpeedIndicatorValue(
            SpeedDirectionIndicatorView indicator,
            float deviationCm) {
        if (indicator == null) {
            return;
        }
        float valueCm = sanitizeDeviation(deviationCm);
        indicator.setSpeed(Math.abs(valueCm));
        indicator.setDirection(directionForDeviation(valueCm));
    }

    private static int directionForDeviation(float deviationCm) {
        if (deviationCm > SPEED_DIRECTION_DEAD_ZONE_CM) {
            return SpeedDirectionIndicatorView.DIRECTION_DOWN_HIGHLIGHT;
        }
        if (deviationCm < -SPEED_DIRECTION_DEAD_ZONE_CM) {
            return SpeedDirectionIndicatorView.DIRECTION_UP_HIGHLIGHT;
        }
        return SpeedDirectionIndicatorView.DIRECTION_NEUTRAL;
    }

    /** 无效输入不保留旧 UI，统一回到零值。 */
    private static float sanitizeDeviation(float deviationCm) {
        return isFinite(deviationCm) ? deviationCm : 0f;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
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
        ViewGroup.MarginLayoutParams marginLayoutParams =
                (ViewGroup.MarginLayoutParams) layoutParams;
        int startMarginPx = Math.round(
                startMarginDp * view.getResources().getDisplayMetrics().density);
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
        ViewGroup.MarginLayoutParams marginLayoutParams =
                (ViewGroup.MarginLayoutParams) layoutParams;
        int endMarginPx = Math.round(
                endMarginDp * view.getResources().getDisplayMetrics().density);
        if (marginLayoutParams.getMarginEnd() == endMarginPx) {
            return;
        }
        marginLayoutParams.setMarginEnd(endMarginPx);
        view.setLayoutParams(marginLayoutParams);
    }
}
