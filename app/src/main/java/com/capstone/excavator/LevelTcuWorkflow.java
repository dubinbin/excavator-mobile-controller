package com.capstone.excavator;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * 找平任务 TCU 协议流程（imu.txt §6.2）。
 * <p>
 * 流程：0x04 进入找平 → 0x10 测点 → 本地算目标高 → 0x11 下发参数 → 0x40 任务确认。
 * 组包/解析见 {@link TcuBusinessCodec}，发送见 {@link TcuLinkHub}。
 */
public final class LevelTcuWorkflow implements TcuLinkHub.BusinessFrameListener {

    private static final String TAG = "LevelTcuWorkflow";
    private static final long REQUEST_TIMEOUT_MS = 8000L;

    public enum Phase {
        IDLE,
        /** 0x84 ActiveFeature=找平 */
        FEATURE_ACTIVE,
        /** 0x90 测点成功 */
        SURVEY_DONE,
        /** 0x91 参数已确认 */
        PARAMS_ACCEPTED,
        /** 0xC0 TaskState=已激活 */
        TASK_ACTIVE
    }

    public interface StepCallback {
        void onSuccess();

        void onError(String message);
    }

    public interface SurveyCallback extends StepCallback {
        void onSurveyResult(double heightM, double lat, double lon);
    }

    /** 0x90 已写入 {@link LevelTaskState} 时通知 UI（不依赖 pending callback 是否仍在）。 */
    public interface SurveyStoredListener {
        void onSurveyStored(double heightM, double lat, double lon);
    }

    private static final LevelTcuWorkflow INSTANCE = new LevelTcuWorkflow();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    private static volatile SurveyStoredListener surveyStoredListener;

    private volatile Phase phase = Phase.IDLE;
    private int pendingExpectAck = -1;
    @Nullable
    private StepCallback pendingCallback;
    @Nullable
    private SurveyCallback pendingSurveyCallback;
    @Nullable
    private Runnable pendingTimeout;

    private LevelTcuWorkflow() {
        TcuLinkHub.addListener(this);
    }

    public static LevelTcuWorkflow getInstance() {
        return INSTANCE;
    }

    public static void setSurveyStoredListener(@Nullable SurveyStoredListener listener) {
        surveyStoredListener = listener;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isFeatureActive() {
        return phase != Phase.IDLE;
    }

    /** 进入找平功能（0x04 Feature=找平 Action=进入）。 */
    public void enterFeature(StepCallback callback) {
        if (!ensureLink(callback)) {
            return;
        }
        sendAndWait(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK,
                TcuBusinessCodec.buildFeatureSelect(
                        TcuBusinessCodec.FEATURE_LEVEL,
                        TcuBusinessCodec.ACTION_ENTER),
                callback);
    }

    /** 通用测点（0x10），{@code pointMode} 与 {@link LevelTaskState#REF_LEFT} 等一致。 */
    public void requestSurvey(int pointMode, SurveyCallback callback) {
        if (!ensureLink(callback)) {
            return;
        }
        if (phase == Phase.IDLE) {
            fail(callback, "请先进入找平功能");
            return;
        }
        int mode = normalizePointMode(pointMode);
        sendAndWaitSurvey(TcuBusinessCodec.MSG_SURVEY_RESULT,
                TcuBusinessCodec.buildSurveyRequest(
                        TcuBusinessCodec.FEATURE_LEVEL,
                        TcuBusinessCodec.POINT_REF,
                        mode),
                callback);
    }

    /**
     * 下发找平目标高度（0x11）。
     * 高度定点：TargetHeight = 测点高度(0x90) + 填挖量。
     * 坐标定点：TargetHeight = 用户设计高程(tvCoordZ)；仍需先完成参考点测点(0x10/0x90)。
     */
    public void submitLevelParams(StepCallback callback) {
        if (!ensureLink(callback)) {
            return;
        }
        if (phase.ordinal() < Phase.SURVEY_DONE.ordinal()) {
            fail(callback, "请先完成参考点测点");
            return;
        }
        if (!LevelTaskState.hasSurveyHeight()) {
            fail(callback, "缺少测点高度，请重新测点");
            return;
        }
        int targetTenthCm;
        if (LevelTaskState.isHeightMode()) {
            double fillOffsetM = LevelTaskState.getTargetHeightM();
            if (Double.isNaN(fillOffsetM)) {
                fail(callback, "请填写填挖量");
                return;
            }
            targetTenthCm = LevelTaskState.getSurveyHeightTenthCm()
                    + TcuBusinessCodec.metersToTenthCm(fillOffsetM);
        } else {
            double designM = LevelTaskState.getTargetZM();
            if (Double.isNaN(designM)) {
                fail(callback, "请填写设计高程");
                return;
            }
            targetTenthCm = TcuBusinessCodec.metersToTenthCm(designM);
        }
        LevelTaskState.setPendingTargetHeightTenthCm(targetTenthCm);
        sendAndWait(TcuBusinessCodec.MSG_LEVEL_PARAMS_ACK,
                TcuBusinessCodec.buildLevelParams(targetTenthCm),
                callback);
    }

    /**
     * WebView 已在本地完成目标高程计算时，直接下发最终目标高程。
     * <p>
     * 这里接收的是最终设计高程，不再重复叠加测点高度；仍要求先完成 0x10/0x90，
     * 以保证流程符合 imu.txt §6.2。
     */
    public void submitLevelParams(double targetHeightM, StepCallback callback) {
        if (!ensureLink(callback)) {
            return;
        }
        if (phase.ordinal() < Phase.SURVEY_DONE.ordinal() || !LevelTaskState.hasSurveyHeight()) {
            fail(callback, "请先完成参考点测点");
            return;
        }
        if (!Double.isFinite(targetHeightM)) {
            fail(callback, "目标高程无效");
            return;
        }
        int targetTenthCm = TcuBusinessCodec.metersToTenthCm(targetHeightM);
        LevelTaskState.setPendingTargetHeightTenthCm(targetTenthCm);
        sendAndWait(TcuBusinessCodec.MSG_LEVEL_PARAMS_ACK,
                TcuBusinessCodec.buildLevelParams(targetTenthCm),
                callback);
    }

    /** 任务确认开始（0x40 Action=确认）。 */
    public void confirmTaskStart(StepCallback callback) {
        if (!ensureLink(callback)) {
            return;
        }
        if (phase.ordinal() < Phase.PARAMS_ACCEPTED.ordinal()) {
            fail(callback, "请先完成找平参数下发");
            return;
        }
        sendAndWait(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK,
                TcuBusinessCodec.buildTaskConfirm(
                        TcuBusinessCodec.FEATURE_LEVEL,
                        TcuBusinessCodec.ACTION_ENTER),
                callback);
    }

    /** 退出找平功能（0x04 Action=退出）。 */
    public void exitFeature(@Nullable StepCallback callback) {
        if (!TcuLinkHub.isConnected()) {
            resetLocal();
            succeed(callback);
            return;
        }
        sendAndWait(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK,
                TcuBusinessCodec.buildFeatureSelect(
                        TcuBusinessCodec.FEATURE_LEVEL,
                        TcuBusinessCodec.ACTION_EXIT),
                new StepCallback() {
                    @Override
                    public void onSuccess() {
                        resetLocal();
                        succeed(callback);
                    }

                    @Override
                    public void onError(String message) {
                        resetLocal();
                        if (callback != null) {
                            callback.onError(message);
                        }
                    }
                });
    }

    public void resetLocal() {
        clearPending();
        phase = Phase.IDLE;
        LevelTaskState.clearTcuSession();
    }

    /** 用户离开页面时取消等待中的请求，避免 UI 一直卡在 busy。 */
    public void cancelPending() {
        clearPending();
    }

    @Override
    public boolean onBusinessFrame(TcuBusinessCodec.ParsedFrame frame) {
        if (frame == null) {
            return false;
        }
        switch (frame.msgId) {
            case TcuBusinessCodec.MSG_FEATURE_SELECT_ACK:
                return handleFeatureSelectAck(frame.data);
            case TcuBusinessCodec.MSG_SURVEY_RESULT:
                return handleSurveyResult(frame.data);
            case TcuBusinessCodec.MSG_LEVEL_PARAMS_ACK:
                return handleLevelParamsAck(frame.data);
            case TcuBusinessCodec.MSG_TASK_CONFIRM_ACK:
                return handleTaskConfirmAck(frame.data);
            default:
                return false;
        }
    }

    private boolean handleFeatureSelectAck(byte[] data) {
        if (!isPending(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK) || data == null || data.length < 3) {
            return false;
        }
        // data[1]=请求 FeatureID 回显；避免挖沟/找平互相吞掉对方的 0x84
        if ((data[1] & 0xFF) != TcuBusinessCodec.FEATURE_LEVEL) {
            return false;
        }
        clearPendingTimeout();
        int result = data[0] & 0xFF;
        int activeFeature = data[2] & 0xFF;
        if (result != TcuBusinessCodec.RESULT_OK) {
            failPending(TcuBusinessCodec.resultMessage(result));
            return true;
        }
        if (activeFeature == TcuBusinessCodec.FEATURE_LEVEL) {
            phase = Phase.FEATURE_ACTIVE;
            succeedPending();
        } else if (activeFeature == TcuBusinessCodec.FEATURE_NONE) {
            phase = Phase.IDLE;
            succeedPending();
        } else {
            failPending("ActiveFeature 异常: 0x" + Integer.toHexString(activeFeature));
        }
        return true;
    }

    private boolean handleSurveyResult(byte[] data) {
        if (!isPending(TcuBusinessCodec.MSG_SURVEY_RESULT) || data == null || data.length < 18) {
            return false;
        }
        // 与挖沟共用 MsgID=0x90：仅处理找平上下文，避免误消费挖沟应答导致挖沟侧一直等超时
        if ((data[1] & 0xFF) != TcuBusinessCodec.FEATURE_LEVEL) {
            return false;
        }
        clearPendingTimeout();
        int result = data[0] & 0xFF;
        if (result != TcuBusinessCodec.RESULT_OK) {
            failPendingSurvey(TcuBusinessCodec.resultMessage(result));
            return true;
        }
        int heightTenthCm = TcuBusinessCodec.readInt32Be(data, 4);
        double lat =  TcuBusinessCodec.parseInt40LatLon(data, 8);
        double lon = TcuBusinessCodec.parseInt40LatLon(data, 13);

        double truncatedLat = (long)(lat * 10000) / 10000.0;
        double truncatedLon = (long)(lon * 10000) / 10000.0;

        double heightM = TcuBusinessCodec.tenthCmToMeters(heightTenthCm);
        LevelTaskState.updateSurveyResult(heightTenthCm, truncatedLat, truncatedLon);
        phase = Phase.SURVEY_DONE;
        notifySurveyStored(heightM, truncatedLat, truncatedLon);
        succeedPendingSurvey(heightM, truncatedLat, truncatedLon);
        return true;
    }

    private void notifySurveyStored(double heightM, double lat, double lon) {
        SurveyStoredListener listener = surveyStoredListener;
        if (listener == null) {
            return;
        }
        mainHandler.post(() -> listener.onSurveyStored(heightM, lat, lon));
    }

    private boolean handleLevelParamsAck(byte[] data) {
        if (!isPending(TcuBusinessCodec.MSG_LEVEL_PARAMS_ACK) || data == null || data.length < 5) {
            return false;
        }
        clearPendingTimeout();
        int result = data[0] & 0xFF;
        if (result != TcuBusinessCodec.RESULT_OK) {
            failPending(TcuBusinessCodec.resultMessage(result));
            return true;
        }
        int acceptedTenthCm = TcuBusinessCodec.readInt32Be(data, 1);
        LevelTaskState.setAcceptedTargetHeightTenthCm(acceptedTenthCm);
        phase = Phase.PARAMS_ACCEPTED;
        succeedPending();
        return true;
    }

    private boolean handleTaskConfirmAck(byte[] data) {
        if (!isPending(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK) || data == null || data.length < 3) {
            return false;
        }
        if ((data[1] & 0xFF) != TcuBusinessCodec.FEATURE_LEVEL) {
            return false;
        }
        clearPendingTimeout();
        int result = data[0] & 0xFF;
        int taskState = data[2] & 0xFF;
        if (result != TcuBusinessCodec.RESULT_OK || taskState != 0x01) {
            failPending("任务未激活: " + TcuBusinessCodec.resultMessage(result));
            return true;
        }
        LevelTaskState.setTcuTaskActive(true);
        phase = Phase.TASK_ACTIVE;
        succeedPending();
        return true;
    }

    private void sendAndWait(int expectAckMsgId, byte[] frame, StepCallback callback) {
        clearPending();
        pendingExpectAck = expectAckMsgId;
        pendingCallback = callback;
        if (!TcuLinkHub.send(frame)) {
            clearPending();
            fail(callback, "发送失败，请检查接收机连接");
            return;
        }
        pendingTimeout = () -> {
            if (isPending(expectAckMsgId)) {
                clearPending();
                fail(callback, "等待 TCU 应答超时");
            }
        };
        mainHandler.postDelayed(pendingTimeout, REQUEST_TIMEOUT_MS);
    }

    /**
     * 测点请求必须在发送前保存 {@link SurveyCallback}。
     * 普通 {@link #sendAndWait} 会先清理 pending，不能在调用它之前设置测点回调。
     */
    private void sendAndWaitSurvey(
            int expectAckMsgId, byte[] frame, SurveyCallback callback) {
        clearPending();
        pendingExpectAck = expectAckMsgId;
        pendingCallback = callback;
        pendingSurveyCallback = callback;
        if (!TcuLinkHub.send(frame)) {
            clearPending();
            fail(callback, "发送失败，请检查接收机连接");
            return;
        }
        pendingTimeout = () -> {
            if (isPending(expectAckMsgId)) {
                clearPending();
                fail(callback, "等待 TCU 应答超时");
            }
        };
        mainHandler.postDelayed(pendingTimeout, REQUEST_TIMEOUT_MS);
    }

    private boolean isPending(int ackMsgId) {
        return pendingExpectAck == ackMsgId;
    }

    private void clearPending() {
        clearPendingTimeout();
        pendingCallback = null;
        pendingSurveyCallback = null;
    }

    private void clearPendingTimeout() {
        pendingExpectAck = -1;
        if (pendingTimeout != null) {
            mainHandler.removeCallbacks(pendingTimeout);
            pendingTimeout = null;
        }
    }

    private static boolean ensureLink(StepCallback callback) {
        if (TcuLinkHub.isConnected()) {
            return true;
        }
        fail(callback, "接收机未连接，请返回主页确认链路状态");
        return false;
    }

    private static int normalizePointMode(int pointMode) {
        if (pointMode < LevelTaskState.REF_LEFT || pointMode > LevelTaskState.REF_RIGHT) {
            return LevelTaskState.REF_MIDDLE;
        }
        return pointMode;
    }

    private void succeedPending() {
        StepCallback cb = pendingCallback;
        pendingCallback = null;
        pendingSurveyCallback = null;
        succeed(cb);
    }

    private void succeedPendingSurvey(double heightM, double lat, double lon) {
        SurveyCallback cb = pendingSurveyCallback;
        pendingCallback = null;
        pendingSurveyCallback = null;
        mainHandler.post(() -> {
            if (cb != null) {
                Log.d(TAG, "survey callback: heightM=" + heightM
                        + ", lat=" + lat + ", lon=" + lon);
                cb.onSurveyResult(heightM, lat, lon);
                cb.onSuccess();
            }
        });
    }

    private void failPending(String message) {
        StepCallback cb = pendingCallback;
        pendingCallback = null;
        pendingSurveyCallback = null;
        fail(cb, message);
    }

    private void failPendingSurvey(String message) {
        SurveyCallback cb = pendingSurveyCallback;
        pendingCallback = null;
        pendingSurveyCallback = null;
        fail(cb, message);
    }

    private static void succeed(@Nullable StepCallback callback) {
        INSTANCE.mainHandler.post(() -> {
            if (callback != null) {
                callback.onSuccess();
            }
        });
    }

    private static void fail(@Nullable StepCallback callback, String message) {
        Log.w(TAG, message);
        INSTANCE.mainHandler.post(() -> {
            if (callback != null) {
                callback.onError(message);
            }
        });
    }
}
