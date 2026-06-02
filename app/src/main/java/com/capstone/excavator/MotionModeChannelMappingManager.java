package com.capstone.excavator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.skydroid.rcsdk.common.callback.CompletionCallback;
import com.skydroid.rcsdk.common.error.SkyException;

/**
 * 运动模式到遥控器通道配置的切换层。
 */
public final class MotionModeChannelMappingManager {

    private static final String TAG = "MotionModeChannel";

    private MotionModeChannelMappingManager() {}

    public static void applyForMode(Context context, int motionModeIndex, CompletionCallback done) {
        if (context == null) {
            if (done != null) done.onResult(new SkyException(-1, "null context"));
            return;
        }

        switch (motionModeIndex) {
            case MotionModeSegmentView.INDEX_CHASSIS:
                Log.i(TAG, "apply chassis default channel mapping");
                JoystickChannelMappingApplier.applyDefaultMapping(
                        context, wrapDoneWithAppliedChannelToast(context, motionModeIndex, done));
                break;
            case MotionModeSegmentView.INDEX_BUCKET:
                Log.i(TAG, "apply saved bucket channel mapping");
                JoystickChannelMappingApplier.applySavedBucketModeMapping(
                        context, wrapDoneWithAppliedChannelToast(context, motionModeIndex, done));
                break;
            case MotionModeSegmentView.INDEX_STOP:
            default:
                Log.i(TAG, "skip channel mapping for motion mode " + motionModeIndex);
                if (done != null) done.onResult(null);
                break;
        }
    }

    /**
     * 下发完成后先回调业务方，再在主线程弹出与本次下发 intent 一致的通道方向说明（与
     * {@link JoystickChannelMappingApplier#applyDefaultMapping} /
     * {@link JoystickChannelMappingApplier#applySavedBucketModeMapping} 使用的 snapshot 对齐）。
     */
    private static CompletionCallback wrapDoneWithAppliedChannelToast(
            Context context, int motionModeIndex, CompletionCallback done) {
        return e -> {
            if (done != null) {
                done.onResult(e);
            }
            if (e != null) {
                return;
            }
            if (motionModeIndex != MotionModeSegmentView.INDEX_CHASSIS
                    && motionModeIndex != MotionModeSegmentView.INDEX_BUCKET) {
                return;
            }
            Context app = context.getApplicationContext();
            ControllerLocalSettings.Snapshot snap = resolveSnapshotForMode(app, motionModeIndex);
            String title = motionModeIndex == MotionModeSegmentView.INDEX_CHASSIS
                    ? "履带模式通道已下发"
                    : "铲斗模式通道已下发";
            
            String msg = title + "\n" + buildAppliedDirectionSummary(snap);
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show());
        };
    }

    public static ControllerLocalSettings.Snapshot resolveSnapshotForMode(
            Context app, int motionModeIndex) {
        if (app == null) {
            return null;
        }
        if (motionModeIndex == MotionModeSegmentView.INDEX_CHASSIS) {
            return ControllerLocalSettings.createDefaultJoystickMappingSnapshot();
        }
        if (motionModeIndex != MotionModeSegmentView.INDEX_BUCKET) {
            return null;
        }
        ControllerLocalSettings.Snapshot local = ControllerLocalSettings.load(app);
        if (local.joystickLeftAb.isEmpty() && local.joystickLeftCd.isEmpty()
                && local.joystickRightEf.isEmpty() && local.joystickRightGh.isEmpty()) {
            return ControllerLocalSettings.createDefaultJoystickMappingSnapshot();
        }
        return local;
    }

    /** ch1/GH … ch4/CD，与 {@link MainActivity} 摇杆通道编号一致。 */
    private static String buildAppliedDirectionSummary(ControllerLocalSettings.Snapshot s) {
        if (s == null) {
            return "";
        }
        return "ch1/GH " + ControllerLocalSettings.formatJoystickDisplay(
                        s.joystickRightGh, s.joystickRightGhReverse)
                + "\nch2/EF " + ControllerLocalSettings.formatJoystickDisplay(
                        s.joystickRightEf, s.joystickRightEfReverse)
                + "\nch3/AB " + ControllerLocalSettings.formatJoystickDisplay(
                        s.joystickLeftAb, s.joystickLeftAbReverse)
                + "\nch4/CD " + ControllerLocalSettings.formatJoystickDisplay(
                        s.joystickLeftCd, s.joystickLeftCdReverse);
    }
}
