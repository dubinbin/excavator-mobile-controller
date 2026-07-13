package com.capstone.excavator;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * 挖沟任务 TCU 协议流程（imu.txt §6.3）。
 * <p>
 * 0x04 进入挖沟 → 0x10 测 A/B → 本地算 A'/B' → 0x20 整包参数 → 0x40 任务确认。
 */
public final class DitchTcuWorkflow implements TcuLinkHub.BusinessFrameListener {

    private static final String TAG = "DitchTcuWorkflow";
    private static final long REQUEST_TIMEOUT_MS = 8000L;
    public static final int DITCH_SQUARE = 0;
    public static final int DITCH_TRAPEZOID = 1;

    public enum Phase {
        IDLE,
        FEATURE_ACTIVE,
        SURVEY_A_DONE,
        SURVEY_B_DONE,
        PARAMS_ACCEPTED,
        TASK_ACTIVE
    }

    public interface StepCallback {
        void onSuccess();

        void onError(String message);
    }

    public interface SurveyCallback extends StepCallback {
        void onSurveyResult(double heightM, double lat, double lon);
    }

    /** Web 已校验的挖沟设计参数；帧编码由 workflow 负责。 */
    public static final class Params {
        final int ditchType;
        final double aLat;
        final double aLon;
        final double aHeightM;
        final double bLat;
        final double bLon;
        final double bHeightM;
        final double depthM;
        final double leftWidthM;
        final double rightWidthM;
        final double topWidthM;

        public Params(int ditchType, double aLat, double aLon, double aHeightM,
                      double bLat, double bLon, double bHeightM, double depthM,
                      double leftWidthM, double rightWidthM, double topWidthM) {
            if (ditchType != DITCH_SQUARE && ditchType != DITCH_TRAPEZOID
                    || !areFinite(aLat, aLon, aHeightM, bLat, bLon, bHeightM,
                    depthM, leftWidthM, rightWidthM, topWidthM)) {
                throw new IllegalArgumentException("挖沟参数无效");
            }
            this.ditchType = ditchType;
            this.aLat = aLat;
            this.aLon = aLon;
            this.aHeightM = aHeightM;
            this.bLat = bLat;
            this.bLon = bLon;
            this.bHeightM = bHeightM;
            this.depthM = depthM;
            this.leftWidthM = leftWidthM;
            this.rightWidthM = rightWidthM;
            this.topWidthM = topWidthM;
        }
    }

    private static final DitchTcuWorkflow INSTANCE = new DitchTcuWorkflow();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile Phase phase = Phase.IDLE;
    private boolean surveyACompleted;
    private boolean surveyBCompleted;
    private int pendingExpectAck = -1;
    private int pendingSurveyPointId = -1;
    @Nullable
    private StepCallback pendingCallback;
    @Nullable
    private SurveyCallback pendingSurveyCallback;
    @Nullable
    private Runnable pendingTimeout;

    private DitchTcuWorkflow() {
        TcuLinkHub.addListener(this);
    }

    public static DitchTcuWorkflow getInstance() {
        return INSTANCE;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isFeatureActive() {
        return phase != Phase.IDLE;
    }

    public void enterFeature(StepCallback callback) {
        if (!ensureLink(callback)) {
            return;
        }
        sendAndWait(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK,
                TcuBusinessCodec.buildFeatureSelect(
                        TcuBusinessCodec.FEATURE_DITCH,
                        TcuBusinessCodec.ACTION_ENTER),
                callback);
    }

    public void requestSurveyA(int pointMode, SurveyCallback callback) {
        requestSurvey(TcuBusinessCodec.POINT_A, pointMode, callback);
    }

    public void requestSurveyB(int pointMode, SurveyCallback callback) {
        requestSurvey(TcuBusinessCodec.POINT_B, pointMode, callback);
    }

    private void requestSurvey(int pointId, int pointMode, SurveyCallback callback) {
        if (!ensureLink(callback)) {
            return;
        }
        if (phase == Phase.IDLE) {
            fail(callback, "请先进入挖沟功能");
            return;
        }
        // 必须在 send 之前写好 pointId / surveyCallback：sendAndWait 内会 clearPending，
        // 且 0x90 若很快返回，不能在「仍以为是 A」或 surveyCallback 已被清掉时处理 B。
        sendAndWaitSurvey(
                TcuBusinessCodec.MSG_SURVEY_RESULT,
                TcuBusinessCodec.buildSurveyRequest(
                        TcuBusinessCodec.FEATURE_DITCH,
                        pointId,
                        normalizePointMode(pointMode)),
                pointId,
                callback);
    }

    /** 下发挖沟参数（0x20）。 */
    public void submitDitchParams(Params params, StepCallback callback) {
        if (!ensureLink(callback)) {
            return;
        }
        if (!surveyACompleted || !surveyBCompleted) {
            fail(callback, "请先完成 A/B 测点");
            return;
        }
        if (params == null) {
            fail(callback, "挖沟参数无效");
            return;
        }
        byte[] frame = TcuBusinessCodec.buildDitchParams(
                params.ditchType,
                params.aLat, params.aLon, TcuBusinessCodec.metersToTenthCm(params.aHeightM),
                params.bLat, params.bLon, TcuBusinessCodec.metersToTenthCm(params.bHeightM),
                TcuBusinessCodec.metersToTenthCm(params.depthM),
                TcuBusinessCodec.metersToTenthCm(params.leftWidthM),
                TcuBusinessCodec.metersToTenthCm(params.rightWidthM),
                TcuBusinessCodec.metersToTenthCm(params.topWidthM));
        sendAndWait(TcuBusinessCodec.MSG_DITCH_PARAMS_ACK, frame, callback);
    }

    public void confirmTaskStart(StepCallback callback) {
        if (!ensureLink(callback)) {
            return;
        }
        if (phase.ordinal() < Phase.PARAMS_ACCEPTED.ordinal()) {
            fail(callback, "请先完成挖沟参数下发");
            return;
        }
        sendAndWait(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK,
                TcuBusinessCodec.buildTaskConfirm(
                        TcuBusinessCodec.FEATURE_DITCH,
                        TcuBusinessCodec.ACTION_ENTER),
                callback);
    }

    public void exitFeature(@Nullable StepCallback callback) {
        if (!TcuLinkHub.isConnected()) {
            resetLocal();
            succeed(callback);
            return;
        }
        sendAndWait(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK,
                TcuBusinessCodec.buildFeatureSelect(
                        TcuBusinessCodec.FEATURE_DITCH,
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
        surveyACompleted = false;
        surveyBCompleted = false;
    }

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
            case TcuBusinessCodec.MSG_DITCH_PARAMS_ACK:
                return handleDitchParamsAck(frame.data);
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
        if ((data[1] & 0xFF) != TcuBusinessCodec.FEATURE_DITCH) {
            return false;
        }
        clearPendingTimeout();
        int result = data[0] & 0xFF;
        int activeFeature = data[2] & 0xFF;
        if (result != TcuBusinessCodec.RESULT_OK) {
            failPending(TcuBusinessCodec.resultMessage(result));
            return true;
        }
        if (activeFeature == TcuBusinessCodec.FEATURE_DITCH) {
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
        // 与找平共用 MsgID=0x90：仅处理挖沟上下文，避免误消费找平应答导致本侧一直等超时
        if ((data[1] & 0xFF) != TcuBusinessCodec.FEATURE_DITCH) {
            return false;
        }
        int expectedPoint = pendingSurveyPointId;
        if ((data[2] & 0xFF) != expectedPoint) {
            // A 点延迟 0x90 在已发 B 测点之后到达：不当作 B 的应答，避免错状态/假超时
            return false;
        }
        clearPendingTimeout();
        int result = data[0] & 0xFF;
        if (result != TcuBusinessCodec.RESULT_OK) {
            failPendingSurvey(TcuBusinessCodec.resultMessage(result));
            return true;
        }

        int heightTenthCm = TcuBusinessCodec.readInt32Be(data, 4);
        double lat = TcuBusinessCodec.parseInt40LatLon(data, 8);
        double lon = TcuBusinessCodec.parseInt40LatLon(data, 13);
        double heightM = TcuBusinessCodec.tenthCmToMeters(heightTenthCm);
        int pointId = expectedPoint;
        if (pointId == TcuBusinessCodec.POINT_A) {
            surveyACompleted = true;
            phase = Phase.SURVEY_A_DONE;
        } else if (pointId == TcuBusinessCodec.POINT_B) {
            surveyBCompleted = true;
            phase = Phase.SURVEY_B_DONE;
        } else {
            failPendingSurvey("测点应答 PointID 异常: 0x" + Integer.toHexString(pointId));
            return true;
        }
        pendingSurveyPointId = -1;
        succeedPendingSurvey(heightM, lat, lon);
        return true;
    }

    private boolean handleDitchParamsAck(byte[] data) {
        if (!isPending(TcuBusinessCodec.MSG_DITCH_PARAMS_ACK) || data == null || data.length < 2) {
            return false;
        }
        clearPendingTimeout();
        int result = data[0] & 0xFF;
        if (result != TcuBusinessCodec.RESULT_OK) {
            failPending(TcuBusinessCodec.resultMessage(result));
            return true;
        }
        phase = Phase.PARAMS_ACCEPTED;
        succeedPending();
        return true;
    }

    private boolean handleTaskConfirmAck(byte[] data) {
        if (!isPending(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK) || data == null || data.length < 3) {
            return false;
        }
        if ((data[1] & 0xFF) != TcuBusinessCodec.FEATURE_DITCH) {
            return false;
        }
        clearPendingTimeout();
        int result = data[0] & 0xFF;
        int taskState = data[2] & 0xFF;
        if (result != TcuBusinessCodec.RESULT_OK || taskState != 0x01) {
            failPending("任务未激活: " + TcuBusinessCodec.resultMessage(result));
            return true;
        }
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
     * 测点 0x10/0x90：{@link #sendAndWait} 会先 {@link #clearPending()} 清掉 surveyCallback，
     * 且旧代码在 send 之后才写 {@code pendingSurveyPointId}，B 点更容易与极快返回的 0x90 竞态导致卡住/丢回调。
     */
    private void sendAndWaitSurvey(
            int expectAckMsgId, byte[] frame, int pointId, SurveyCallback callback) {
        clearPending();
        pendingSurveyPointId = pointId;
        pendingSurveyCallback = callback;
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

    private boolean isPending(int ackMsgId) {
        return pendingExpectAck == ackMsgId;
    }

    private void clearPending() {
        clearPendingTimeout();
        pendingCallback = null;
        pendingSurveyCallback = null;
        pendingSurveyPointId = -1;
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
        return pointMode < 0 || pointMode > 2 ? 1 : pointMode;
    }

    private static boolean areFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
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
        pendingSurveyPointId = -1;
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
