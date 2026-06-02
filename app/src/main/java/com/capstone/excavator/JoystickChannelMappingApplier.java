package com.capstone.excavator;

import android.content.Context;
import android.util.Log;

import com.skydroid.rcsdk.common.callback.CompletionCallback;
import com.skydroid.rcsdk.common.error.SkyException;

import java.util.Arrays;
import java.util.Locale;

/**
 * 把 {@link ControllerLocalSettings} 中保存的「摇杆轴(AB/CD/EF/GH) → 挖机动作(大臂/小臂/铲斗/回旋)」
 * 映射，通过 {@link RcChannelSettingsHelper} 下发到云卓控制器的 {@code ChannelSettings.mapping}。
 *
 * <p>整体思路：「按动作交换 mapping 整型」。我们不预先知道控制器各动作具体对应的 mapping 整数，
 * 而是首次读 {@code ChannelSettings} 时按用户陈述的默认布局（ch1=铲斗, ch2=大臂, ch3=小臂, ch4=回旋）
 * 把当时 channels[0..3] 的 mapping 整数快照为各动作的基准码（参见 {@link JoystickMappingChannelCodes}）。
 * 之后任何一次设置页保存，都按用户选择把对应基准码写到对应 channel index 即可。
 *
 * <p>基准前提：首次成功捕获时，控制器仍为默认布局；这与 {@code SettingsActivity} 默认下拉一致。
 * 后续如需「重新校准」，可清除 {@link JoystickMappingChannelCodes} 后重连。
 */
public final class JoystickChannelMappingApplier {

    private static final String TAG = "JoyMappingApplier";

    /** 在 {@code KeyChannels} 数组中的物理槽位下标，与 {@link RcChannelSettingsHelper} 常量保持一致。 */
    private static final int CH_IDX_GH = RcChannelSettingsHelper.KEY_CH_RIGHT_LR; // 0
    private static final int CH_IDX_EF = RcChannelSettingsHelper.KEY_CH_RIGHT_UD; // 1
    private static final int CH_IDX_AB = RcChannelSettingsHelper.KEY_CH_LEFT_UD;  // 2
    private static final int CH_IDX_CD = RcChannelSettingsHelper.KEY_CH_LEFT_LR;  // 3

    private JoystickChannelMappingApplier() {}

    /**
     * 首次连接遥控器后调用：若本地尚无基准码，则读一次 {@code ChannelSettings} 并按
     * 默认布局快照。已快照过则什么都不做。
     */
    public static void captureBaselineIfNeeded(Context context) {
        if (context == null) return;
        if (JoystickMappingChannelCodes.hasBaseline(context)) {
            return;
        }
        RcChannelSettingsHelper.readJoystickMappingByIndex(context,
                new RcChannelSettingsHelper.MappingReadCallback() {
                    @Override
                    public void onSuccess(int[] mappingByIndex) {
                        JoystickMappingChannelCodes.Baseline base =
                                buildBaselineFromDefaultLayout(mappingByIndex);
                        if (base == null) {
                            Log.w(TAG, "baseline capture skipped: invalid channel mappings");
                            return;
                        }
                        JoystickMappingChannelCodes.saveBaseline(context, base);
                        Log.i(TAG, String.format(Locale.US,
                                "baseline captured: bucket=%d boom=%d stick=%d swing=%d",
                                base.bucketCode, base.boomCode, base.stickCode, base.swingCode));
                    }

                    @Override
                    public void onFailure(SkyException e) {
                        Log.w(TAG, "baseline capture failed in captureBaselineIfNeeded: " + (e != null ? e.getMessage() : "?"));
                    }
                });
    }

    /**
     * 把 {@code local} 中持久化的 AB/CD/EF/GH→动作映射下发到遥控器。
     *
     * <p>当基准码尚未捕获时，会立即读一次当前 {@code ChannelSettings}，并按默认关系建立基准：
     * ch1/GH=铲斗、ch2/EF=大臂、ch3/AB=小臂、ch4/CD=回旋；建立成功后继续下发本次设置。
     */
    public static void applyUserMapping(Context context,
                                        ControllerLocalSettings.Snapshot local,
                                        CompletionCallback done) {
        if (context == null || local == null) {
            if (done != null) done.onResult(new SkyException(-1, "null context / snapshot"));
            return;
        }
        if (!JoystickMappingChannelCodes.hasBaseline(context)) {
            Log.i(TAG, "baseline missing; capturing from current ChannelSettings before apply");
            RcChannelSettingsHelper.readJoystickMappingByIndex(context,
                    new RcChannelSettingsHelper.MappingReadCallback() {
                        @Override
                        public void onSuccess(int[] mappingByIndex) {
                            JoystickMappingChannelCodes.Baseline base =
                                    buildBaselineFromDefaultLayout(mappingByIndex);
                            if (base == null) {
                                if (done != null) {
                                    done.onResult(new SkyException(-1,
                                            "baseline capture failed: "
                                                    + Arrays.toString(mappingByIndex)));
                                }
                                return;
                            }
                            JoystickMappingChannelCodes.saveBaseline(context, base);
                            Log.i(TAG, String.format(Locale.US,
                                    "baseline captured before apply: bucket=%d boom=%d stick=%d swing=%d",
                                    base.bucketCode, base.boomCode, base.stickCode, base.swingCode));
                            applyUserMappingWithBaseline(context, local, base, done);
                        }

                        @Override
                        public void onFailure(SkyException e) {
                            Log.w(TAG, "baseline capture before apply failed: "
                                    + (e != null ? e.getMessage() : "?"));
                            if (done != null) done.onResult(e != null ? e
                                    : new SkyException(-1, "baseline capture failed"));
                        }
                    });
            return;
        }
        JoystickMappingChannelCodes.Baseline base = JoystickMappingChannelCodes.load(context);
        applyUserMappingWithBaseline(context, local, base, done);
    }

    /**
     * 下发设置页保存的铲斗模式通道配置。该配置已由 {@link ControllerLocalSettings#save}
     * 持久化到本地，运动模式切回铲斗时从这里读取并恢复。
     */
    public static void applySavedBucketModeMapping(Context context, CompletionCallback done) {
        if (context == null) {
            if (done != null) done.onResult(new SkyException(-1, "null context"));
            return;
        }
        ControllerLocalSettings.Snapshot local = ControllerLocalSettings.load(context);
        if (local.joystickLeftAb.isEmpty() && local.joystickLeftCd.isEmpty()
                && local.joystickRightEf.isEmpty() && local.joystickRightGh.isEmpty()) {
            local = ControllerLocalSettings.createDefaultJoystickMappingSnapshot();
        }
        applyUserMapping(context, local, done);
    }

    /**
     * 下发系统默认通道布局，用于底盘模式：
     * AB/ch3=小臂、CD/ch4=回旋、EF/ch2=大臂、GH/ch1=铲斗。
     *
     */
    public static void applyDefaultMapping(Context context, CompletionCallback done) {
        ControllerLocalSettings.Snapshot snapshot =
                ControllerLocalSettings.createDefaultJoystickMappingSnapshot();
        applyUserMapping(context, snapshot, done);
    }

    /**
     * 按默认物理布局从 {@code channels[0..3].mapping} 推导动作基准码：
     * GH/ch1=铲斗、EF/ch2=大臂、AB/ch3=小臂、CD/ch4=回旋。
     */
    private static JoystickMappingChannelCodes.Baseline buildBaselineFromDefaultLayout(int[] mappingByIndex) {
        if (mappingByIndex == null || mappingByIndex.length < 4) {
            return null;
        }
        for (int i = 0; i < 4; i++) {
            if (mappingByIndex[i] < 0) {
                return null;
            }
        }
        return new JoystickMappingChannelCodes.Baseline(
                mappingByIndex[CH_IDX_GH], // 铲斗
                mappingByIndex[CH_IDX_EF], // 大臂
                mappingByIndex[CH_IDX_AB], // 小臂
                mappingByIndex[CH_IDX_CD]  // 回旋
        );
    }

    private static void applyUserMappingWithBaseline(
            Context context,
            ControllerLocalSettings.Snapshot local,
            JoystickMappingChannelCodes.Baseline base,
            CompletionCallback done) {
        int[] target = new int[4];
        Arrays.fill(target, -1);
        target[CH_IDX_AB] = base.codeFor(ControllerLocalSettings.motionLabelToKey(local.joystickLeftAb));
        target[CH_IDX_CD] = base.codeFor(ControllerLocalSettings.motionLabelToKey(local.joystickLeftCd));
        target[CH_IDX_EF] = base.codeFor(ControllerLocalSettings.motionLabelToKey(local.joystickRightEf));
        target[CH_IDX_GH] = base.codeFor(ControllerLocalSettings.motionLabelToKey(local.joystickRightGh));

        for (int i = 0; i < 4; i++) {
            if (target[i] < 0) {
                Log.w(TAG, "skip apply: unresolved code at channel index " + i
                        + " (AB=" + local.joystickLeftAb + " CD=" + local.joystickLeftCd
                        + " EF=" + local.joystickRightEf + " GH=" + local.joystickRightGh + ")");
                if (done != null) done.onResult(new SkyException(-1, "unresolved mapping code"));
                return;
            }
        }

        Boolean[] reverse = new Boolean[4];
        reverse[CH_IDX_AB] = local.joystickLeftAbReverse;
        reverse[CH_IDX_CD] = local.joystickLeftCdReverse;
        reverse[CH_IDX_EF] = local.joystickRightEfReverse;
        reverse[CH_IDX_GH] = local.joystickRightGhReverse;

        Log.i(TAG, String.format(Locale.US,
                "applying mappingByIndex=[GH=%d, EF=%d, AB=%d, CD=%d] reverseByIndex=[GH=%b, EF=%b, AB=%b, CD=%b]",
                target[CH_IDX_GH], target[CH_IDX_EF], target[CH_IDX_AB], target[CH_IDX_CD],
                reverse[CH_IDX_GH], reverse[CH_IDX_EF], reverse[CH_IDX_AB], reverse[CH_IDX_CD]));
        RcChannelSettingsHelper.setJoystickMappingAndReverseByIndex(context, target, reverse, e -> {
            if (e == null) {
                Log.i(TAG, "apply success");
            } else {
                Log.e(TAG, "apply failed: " + e.getMessage());
            }
            if (done != null) done.onResult(e);
        });
    }
}
