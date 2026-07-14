package com.capstone.excavator;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

public final class SlopeRepairTcuWorkflow implements TcuLinkHub.BusinessFrameListener {

    private static final String TAG = "SlopeRepairWorkflow";
    private static final long REQUEST_TIMEOUT_MS = 8000L;
    public static final int TYPE_TOP_LINE = 0;
    public static final int TYPE_BOTTOM_LINE = 1;

    public enum Phase {
        IDLE,
        FEATURE_ACTIVE,
        SURVEY_A_DONE,
        SURVEY_B_DONE,
        SURVEY_C_DONE,
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

    /** Web 已校验的修坡设计参数；帧编码由 workflow 负责。 */
    public static final class Params {
        final int repairType;
        final double aLat, aLon, aHeightM;
        final double bLat, bLon, bHeightM;
        final double cLat, cLon, cHeightM;
        final double slopeRatio, verticalHeightM, horizontalDistanceM, abDistanceM, abLiftM;

        public Params(int repairType, double aLat, double aLon, double aHeightM,
                      double bLat, double bLon, double bHeightM,
                      double cLat, double cLon, double cHeightM, double slopeRatio,
                      double verticalHeightM, double horizontalDistanceM,
                      double abDistanceM, double abLiftM) {
            if (repairType != TYPE_TOP_LINE && repairType != TYPE_BOTTOM_LINE
                    || !areFinite(aLat, aLon, aHeightM, bLat, bLon, bHeightM,
                    cLat, cLon, cHeightM, slopeRatio, verticalHeightM,
                    horizontalDistanceM, abDistanceM, abLiftM)) {
                throw new IllegalArgumentException("修坡参数无效");
            }
            this.repairType = repairType;
            this.aLat = aLat; this.aLon = aLon; this.aHeightM = aHeightM;
            this.bLat = bLat; this.bLon = bLon; this.bHeightM = bHeightM;
            this.cLat = cLat; this.cLon = cLon; this.cHeightM = cHeightM;
            this.slopeRatio = slopeRatio;
            this.verticalHeightM = verticalHeightM;
            this.horizontalDistanceM = horizontalDistanceM;
            this.abDistanceM = abDistanceM;
            this.abLiftM = abLiftM;
        }
    }

    private static final SlopeRepairTcuWorkflow INSTANCE = new SlopeRepairTcuWorkflow();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile Phase phase = Phase.IDLE;
    private int surveyedPointsMask;
    private int pendingExpectAck = -1;
    private int pendingSurveyPointId = -1;
    @Nullable
    private StepCallback pendingCallback;
    @Nullable
    private SurveyCallback pendingSurveyCallback;
    @Nullable
    private Runnable pendingTimeout;

    private SlopeRepairTcuWorkflow() {
        TcuLinkHub.addListener(this);
    }

    public static SlopeRepairTcuWorkflow getInstance() {
        return INSTANCE;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isFeatureActive() {
        return phase != Phase.IDLE;
    }

    public void enterFeature(StepCallback callback) {
        if (!ensureLink(callback)) return;
        sendAndWait(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK,
                TcuBusinessCodec.buildFeatureSelect(
                        TcuBusinessCodec.FEATURE_SLOPE,
                        TcuBusinessCodec.ACTION_ENTER),
                callback);
    }

    public void requestSurvey(int pointId, int pointMode, SurveyCallback callback) {
        if (!ensureLink(callback)) return;
        if (phase == Phase.IDLE) {
            fail(callback, "请先进入修坡功能");
            return;
        }
        sendAndWaitSurvey(
                TcuBusinessCodec.MSG_SURVEY_RESULT,
                TcuBusinessCodec.buildSurveyRequest(
                        TcuBusinessCodec.FEATURE_SLOPE,
                        pointId,
                        normalizePointMode(pointMode)),
                pointId,
                callback);
    }

    /** 将全局任务快照转换为协议字段并下发修坡参数（0x30）。 */
    public void submitSlopeParams(
            SlopeRepairTaskState.TaskParameters taskParameters,
            StepCallback callback) {
        if (taskParameters == null) {
            fail(callback, "修坡参数无效");
            return;
        }
        submitSlopeParams(new Params(
                taskParameters.repairType,
                taskParameters.pointA.latitude,
                taskParameters.pointA.longitude,
                taskParameters.pointA.heightM,
                taskParameters.pointB.latitude,
                taskParameters.pointB.longitude,
                taskParameters.pointB.heightM,
                taskParameters.pointC.latitude,
                taskParameters.pointC.longitude,
                taskParameters.pointC.heightM,
                taskParameters.slopeRatio,
                taskParameters.verticalHeightM,
                taskParameters.horizontalDistanceM,
                taskParameters.abDistanceM,
                taskParameters.abLiftM), callback);
    }

    /** 下发已经转换为协议字段的修坡参数（0x30）。 */
    public void submitSlopeParams(Params params, StepCallback callback) {
        if (!ensureLink(callback)) return;
        if (params == null) {
            fail(callback, "修坡参数无效");
            return;
        }
        byte[] frame = TcuBusinessCodec.buildSlopeParams(
                params.repairType,
                params.aLat, params.aLon, TcuBusinessCodec.metersToTenthCm(params.aHeightM),
                params.bLat, params.bLon, TcuBusinessCodec.metersToTenthCm(params.bHeightM),
                params.cLat, params.cLon, TcuBusinessCodec.metersToTenthCm(params.cHeightM),
                (int) Math.round(params.slopeRatio * 1000.0),
                TcuBusinessCodec.metersToTenthCm(params.verticalHeightM),
                TcuBusinessCodec.metersToTenthCm(params.horizontalDistanceM),
                TcuBusinessCodec.metersToTenthCm(params.abDistanceM),
                TcuBusinessCodec.metersToTenthCm(params.abLiftM));
        sendAndWait(TcuBusinessCodec.MSG_SLOPE_PARAMS_ACK, frame, callback);
    }

    public void confirmTaskStart(StepCallback callback) {
        if (!ensureLink(callback)) return;
        if (phase.ordinal() < Phase.PARAMS_ACCEPTED.ordinal()) {
            fail(callback, "请先完成修坡参数下发");
            return;
        }
        sendAndWait(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK,
                TcuBusinessCodec.buildTaskConfirm(
                        TcuBusinessCodec.FEATURE_SLOPE,
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
                        TcuBusinessCodec.FEATURE_SLOPE,
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
                        if (callback != null) callback.onError(message);
                    }
                });
    }

    public void resetLocal() {
        clearPending();
        phase = Phase.IDLE;
        surveyedPointsMask = 0;
    }

    public void cancelPending() {
        clearPending();
    }

    @Override
    public boolean onBusinessFrame(TcuBusinessCodec.ParsedFrame frame) {
        if (frame == null) return false;
        switch (frame.msgId) {
            case TcuBusinessCodec.MSG_FEATURE_SELECT_ACK:
                return handleFeatureSelectAck(frame.data);
            case TcuBusinessCodec.MSG_SURVEY_RESULT:
                return handleSurveyResult(frame.data);
            case TcuBusinessCodec.MSG_SLOPE_PARAMS_ACK:
                return handleSlopeParamsAck(frame.data);
            case TcuBusinessCodec.MSG_TASK_CONFIRM_ACK:
                return handleTaskConfirmAck(frame.data);
            default:
                return false;
        }
    }

    private boolean handleFeatureSelectAck(byte[] data) {
        if (!isPending(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK) || data == null || data.length < 3) return false;
        if ((data[1] & 0xFF) != TcuBusinessCodec.FEATURE_SLOPE) return false;
        clearPendingTimeout();
        int result = data[0] & 0xFF;
        int activeFeature = data[2] & 0xFF;
        if (result != TcuBusinessCodec.RESULT_OK) {
            failPending(TcuBusinessCodec.resultMessage(result));
            return true;
        }
        if (activeFeature == TcuBusinessCodec.FEATURE_SLOPE) {
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
        if (!isPending(TcuBusinessCodec.MSG_SURVEY_RESULT) || data == null || data.length < 18) return false;
        if ((data[1] & 0xFF) != TcuBusinessCodec.FEATURE_SLOPE) return false;
        int expectedPoint = pendingSurveyPointId;
        if ((data[2] & 0xFF) != expectedPoint) return false;
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
        surveyedPointsMask |= 1 << expectedPoint;
        if (expectedPoint == TcuBusinessCodec.POINT_A) {
            phase = Phase.SURVEY_A_DONE;
        } else if (expectedPoint == TcuBusinessCodec.POINT_B) {
            phase = Phase.SURVEY_B_DONE;
        } else if (expectedPoint == TcuBusinessCodec.POINT_C) {
            phase = Phase.SURVEY_C_DONE;
        }
        pendingSurveyPointId = -1;
        succeedPendingSurvey(heightM, lat, lon);
        return true;
    }

    private boolean handleSlopeParamsAck(byte[] data) {
        if (!isPending(TcuBusinessCodec.MSG_SLOPE_PARAMS_ACK) || data == null || data.length < 2) return false;
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
        if (!isPending(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK) || data == null || data.length < 3) return false;
        if ((data[1] & 0xFF) != TcuBusinessCodec.FEATURE_SLOPE) return false;
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

    private void sendAndWaitSurvey(int expectAckMsgId, byte[] frame, int pointId, SurveyCallback callback) {
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
        if (TcuLinkHub.isConnected()) return true;
        fail(callback, "接收机未连接，请返回主页确认链路状态");
        return false;
    }

    private static int normalizePointMode(int pointMode) {
        return pointMode < 0 || pointMode > 2
                ? 1
                : pointMode;
    }

    private static int requiredSurveyMask() {
        return (1 << TcuBusinessCodec.POINT_A)
                | (1 << TcuBusinessCodec.POINT_B)
                | (1 << TcuBusinessCodec.POINT_C);
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
            if (callback != null) callback.onSuccess();
        });
    }

    private static void fail(@Nullable StepCallback callback, String message) {
        Log.w(TAG, message);
        INSTANCE.mainHandler.post(() -> {
            if (callback != null) callback.onError(message);
        });
    }
}
