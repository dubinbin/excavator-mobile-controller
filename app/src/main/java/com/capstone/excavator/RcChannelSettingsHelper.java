package com.capstone.excavator;

import android.content.Context;
import android.util.Log;

import com.skydroid.rcsdk.KeyManager;
import com.skydroid.rcsdk.common.callback.CompletionCallback;
import com.skydroid.rcsdk.common.callback.CompletionCallbackWith;
import com.skydroid.rcsdk.common.error.SkyException;
import com.skydroid.rcsdk.common.remotecontroller.ChannelItem;
import com.skydroid.rcsdk.common.remotecontroller.ChannelSettings;
import com.skydroid.rcsdk.key.RemoteControllerKey;

/**
 * 云卓 RCSDK 摇杆/通道表 GET、SET 辅助；与 {@link com.skydroid.rcsdk.key.RemoteControllerKey#getKeyChannels()} 下标说明见类内常量。
 */
public final class RcChannelSettingsHelper {

    private static final String TAG = "RcChannel";

    /** 设为 false 可关闭连接后自动打印通道表。 */
    public static volatile boolean LOG_ON_RC_CONNECTED = true;

    public static final int KEY_CH_RIGHT_LR = 0;
    public static final int KEY_CH_RIGHT_UD = 1;
    public static final int KEY_CH_LEFT_UD = 2;
    public static final int KEY_CH_LEFT_LR = 3;


    private RcChannelSettingsHelper() {}

    public static void logChannelSettingsAfterDelay(Context context, long delayMs) {
        if (!LOG_ON_RC_CONNECTED) {
            return;
        }
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(() -> logChannelSettings(context), delayMs);
    }

    public static void logChannelSettings(Context context) {
        KeyManager.INSTANCE.get(RemoteControllerKey.INSTANCE.getKeyChannelSettings(),
            new CompletionCallbackWith<ChannelSettings>() {
                @Override
                public void onSuccess(ChannelSettings settings) {
                    if (settings == null) {
                        Log.i(TAG, "KeyChannelSettings: null");
                        return;
                    }
                    ChannelItem[] ch = settings.getChannels();
                    Log.i(TAG, "KeyChannelSettings channels=" + (ch == null ? "null" : ch.length));
                    if (ch != null) {
                        for (int i = 0; i < ch.length; i++) {
                            ChannelItem it = ch[i];
                            if (it != null) {
                                Log.i(TAG, "  [" + i + "] " + it.toString());
                            }
                        }
                    }
                }

                @Override
                public void onFailure(SkyException e) {
                    Log.e(TAG, "GET KeyChannelSettings failed in logChannelSettings: " + (e != null ? e.getMessage() : "?"));
                }
            });
        
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 摇杆四轴（channel index 0..3）的「mapping 整型」批量读 / 写。供
    // {@link JoystickChannelMappingApplier} 在 SettingsActivity 保存时下发新的
    // 摇杆通道→动作映射，无需重新设置 min/middle/max/reverse。
    // ─────────────────────────────────────────────────────────────────────────────

    public interface MappingReadCallback {
        /** mappingByIndex 长度 4，下标 0..3 对应 channel index 0..3；某一槽不存在记 -1。 */
        void onSuccess(int[] mappingByIndex);
        void onFailure(SkyException e);
    }

    /**
     * 读取 channel index 0..3 当前的 mapping 整型，便于业务层（如基准快照）使用。
     */
    public static void readJoystickMappingByIndex(Context context, MappingReadCallback cb) {
        if (cb == null) return;
        KeyManager.INSTANCE.get(RemoteControllerKey.INSTANCE.getKeyChannelSettings(),
            new CompletionCallbackWith<ChannelSettings>() {
                @Override
                public void onSuccess(ChannelSettings settings) {
                    if (settings == null || settings.getChannels() == null) {
                        cb.onFailure(new SkyException(-1, "null ChannelSettings"));
                        return;
                    }
                    cb.onSuccess(collectMappingByIndex(settings.getChannels()));
                }

                @Override
                public void onFailure(SkyException e) {
                    cb.onFailure(e);
                }
            });
        }
    

    /**
     * 同时更新 channel index 0..3 的 {@code mapping} 与 {@code reverse}，
     * 其余 min/middle/max 保留原值。
     *
     * @param mappingByIndex 长度 4；元素 &lt; 0 表示 mapping 不变。
     * @param reverseByIndex 长度 4 的可空 {@link Boolean} 数组；{@code null} 元素表示该槽 reverse 不变；
     *                       整个数组为 {@code null} 时所有槽 reverse 不变。
     */
    public static void setJoystickMappingAndReverseByIndex(Context context,
                                                           int[] mappingByIndex,
                                                           Boolean[] reverseByIndex,
                                                           CompletionCallback done) {
        if (mappingByIndex == null || mappingByIndex.length < 4) {
            if (done != null) done.onResult(new SkyException(-1, "mappingByIndex length<4"));
            return;
        }
        final int[] mapTarget = new int[] {
                mappingByIndex[0], mappingByIndex[1], mappingByIndex[2], mappingByIndex[3]
        };
        final Boolean[] revTarget = new Boolean[] {
                reverseByIndex != null && reverseByIndex.length > 0 ? reverseByIndex[0] : null,
                reverseByIndex != null && reverseByIndex.length > 1 ? reverseByIndex[1] : null,
                reverseByIndex != null && reverseByIndex.length > 2 ? reverseByIndex[2] : null,
                reverseByIndex != null && reverseByIndex.length > 3 ? reverseByIndex[3] : null
        };

        KeyManager.INSTANCE.get(RemoteControllerKey.INSTANCE.getKeyChannelSettings(),
            new CompletionCallbackWith<ChannelSettings>() {
                @Override
                public void onSuccess(ChannelSettings settings) {
                    if (settings == null || settings.getChannels() == null) {
                        if (done != null) done.onResult(new SkyException(-1, "null ChannelSettings"));
                        return;
                    }
                    applyMappingAndReverseByIndex(settings.getChannels(), mapTarget, revTarget);
                    KeyManager.INSTANCE.set(
                            RemoteControllerKey.INSTANCE.getKeyChannelSettings(),
                            settings,
                            e -> {
                                if (done != null) done.onResult(e);
                            });
                }

                @Override
                public void onFailure(SkyException e) {
                    if (done != null) done.onResult(e);
                }
            });
    }

    private static int[] collectMappingByIndex(ChannelItem[] channels) {
        int[] out = new int[] { -1, -1, -1, -1 };
        boolean oneBased = usesOneBasedChannelIndex(channels);
        for (int pos = 0; pos < channels.length; pos++) {
            ChannelItem it = channels[pos];
            if (it == null) continue;
            int idx = logicalJoystickIndex(it, pos, oneBased);
            if (idx >= 0 && idx < out.length) {
                out[idx] = it.getMapping();
            }
        }
        return out;
    }

    private static void applyMappingAndReverseByIndex(ChannelItem[] channels,
                                                      int[] mapTarget,
                                                      Boolean[] revTarget) {
        boolean oneBased = usesOneBasedChannelIndex(channels);
        for (int pos = 0; pos < channels.length; pos++) {
            ChannelItem it = channels[pos];
            if (it == null) continue;
            int idx = logicalJoystickIndex(it, pos, oneBased);
            if (idx < 0 || idx >= mapTarget.length) continue;
            if (mapTarget[idx] >= 0) {
                it.setMapping(mapTarget[idx]);
            }
            if (revTarget[idx] != null) {
                it.setReverse(revTarget[idx]);
            }
        }
    }

    /**
     * 不同遥控器 / SDK 版本里 {@link ChannelItem#getIndex()} 可能是 0-based（0..3），
     * 也可能是 1-based（1..4）。要先对整批 channels 判断编号体系，不能逐项判断：
     * 否则 1-based 的 1/2/3 会被误认成 0-based，造成 index 0 缺失。
     */
    private static boolean usesOneBasedChannelIndex(ChannelItem[] channels) {
        boolean hasZero = false;
        boolean hasFour = false;
        if (channels == null) {
            return false;
        }
        for (ChannelItem it : channels) {
            if (it == null) continue;
            int raw = it.getIndex();
            if (raw == 0) hasZero = true;
            if (raw == 4) hasFour = true;
        }
        return hasFour && !hasZero;
    }

    private static int logicalJoystickIndex(ChannelItem item, int arrayPosition, boolean oneBased) {
        if (item == null) {
            return -1;
        }
        int raw = item.getIndex();
        if (oneBased && raw >= 1 && raw <= 4) {
            return raw - 1;
        }
        if (!oneBased && raw >= 0 && raw <= 3) {
            return raw;
        }
        if (arrayPosition >= 0 && arrayPosition <= 3) {
            return arrayPosition;
        }
        return -1;
    }
}
