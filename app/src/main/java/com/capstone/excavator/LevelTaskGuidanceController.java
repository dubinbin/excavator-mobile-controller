package com.capstone.excavator;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.Random;

/**
 * 找平 / 挖沟作业引导 UI 控制器。
 *
 * <p>本类只负责把外部已经计算好的左右偏离量显示到 View，不读取任务状态、TCU 或 IMU
 * 数据，也不负责填挖量、斗尖位置、限幅或平滑计算。</p>
 *
 * <p>偏离量单位统一为厘米：正值表示偏高（需要向下），负值表示偏低（需要向上）。</p>
 */
public final class LevelTaskGuidanceController {

    private static final String TAG = "LevelGuidanceUi";

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

    /** 左右数值及方向卡。 */
    private SpeedDirectionIndicatorView leftSpeedIndicator;
    private SpeedDirectionIndicatorView rightSpeedIndicator;

    private final Random mockDeviationRandom = new Random();

    /**
     * 绑定两组引导 View。在 Activity {@code setContentView} 之后调用一次。
     */
    public void bind(Activity activity) {
        leftActivityGauge = activity.findViewById(R.id.leftActivityGauge);
        rightActivityGauge = activity.findViewById(R.id.rightActivityGauge);
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
        setGaugeValue(leftActivityGauge, leftDeviationCm);
        setGaugeValue(rightActivityGauge, rightDeviationCm);
    }

    /**
     * 临时模拟入口：生成两个独立随机偏离量并只更新左右光谱竖条。
     *
     * <p>真实 TCU 填挖 / 机身偏离公式接入后，调用方应改用
     * {@link #updateActivityGaugeDeviations(float, float)}。</p>
     */
    public void updateMockActivityGaugeDeviations() {
        float leftDeviationCm = nextMockDeviationCm();
        float rightDeviationCm = nextMockDeviationCm();
        updateActivityGaugeDeviations(leftDeviationCm, rightDeviationCm);
        Log.d(TAG, "mock gauges: leftCm=" + leftDeviationCm
                + ", rightCm=" + rightDeviationCm);
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

    /** 清空左右光谱竖条，不影响速度方向卡。 */
    public void clearActivityGauges() {
        setGaugeValue(leftActivityGauge, 0f);
        setGaugeValue(rightActivityGauge, 0f);
    }

    /** 清空左右速度方向卡，不影响光谱竖条。 */
    public void clearSpeedIndicators() {
        setSpeedIndicatorValue(leftSpeedIndicator, 0f);
        setSpeedIndicatorValue(rightSpeedIndicator, 0f);
    }

    /** 清空两组引导 View。 */
    public void clear() {
        clearActivityGauges();
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

    private static void setGaugeValue(VerticalSpectrumGaugeView gauge, float deviationCm) {
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

    private float nextMockDeviationCm() {
        return (mockDeviationRandom.nextFloat() * 2f - 1f)
                * DEFAULT_ACTIVITY_GAUGE_RANGE_CM;
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
