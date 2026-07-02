package com.capstone.excavator;

import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * WebView 任务页与三个 TCU Workflow 之间的适配层。
 *
 * <p>Web 只负责收集/展示任务参数；TCU 帧发送、应答校验、超时和协议阶段仍由
 * {@link LevelTcuWorkflow}、{@link DitchTcuWorkflow}、{@link SlopeRepairTcuWorkflow} 管理。
 */
final class WebTaskTcuController {

    interface Host {
        void sendToWeb(String messageJson);

        void showError(String message);

        void onTaskActivated(TaskTypeState.Type taskType);
    }

    private enum TaskKind {
        LEVEL(TcuBusinessCodec.FEATURE_LEVEL),
        DITCH(TcuBusinessCodec.FEATURE_DITCH),
        SLOPE(TcuBusinessCodec.FEATURE_SLOPE);

        final int featureId;

        TaskKind(int featureId) {
            this.featureId = featureId;
        }
    }

    private static final String TAG = "WebTaskTcuController";

    static final String WEB_EVENT_READY = "WEBVIEW_READY";
    static final String WEB_EVENT_MEASUREMENT = "MEASUREMENT_POINT_CALL";
    static final String WEB_EVENT_LEVEL_START = "LEVEL_TASK_START";
    static final String WEB_EVENT_DITCH_START = "DIG_TASK_START";
    static final String WEB_EVENT_SLOPE_START = "REPAIR_SLOPE_START";

    private static final String NATIVE_EVENT_MEASUREMENT = "MEASUREMENT_POINT_RECEIVE";
    private static final String NATIVE_EVENT_WORKFLOW_STATE = "TCU_WORKFLOW_STATE";
    private static final String NATIVE_EVENT_WORKFLOW_ERROR = "TCU_WORKFLOW_ERROR";

    private final Host host;
    private final TaskKind taskKind;
    private boolean featureEnterRequested;
    private boolean startRequested;
    private boolean taskActivated;
    private boolean destroyed;

    WebTaskTcuController(Host host, String initialRoute) {
        this.host = host;
        this.taskKind = taskKindForRoute(initialRoute);
    }

    boolean handlesTaskRoute() {
        return taskKind != null;
    }

    void onWebReady() {
        if (taskKind == null || destroyed || featureEnterRequested) {
            return;
        }
        featureEnterRequested = true;
        enterFeature();
    }

    boolean onWebMessage(String type, @Nullable JSONObject payload) {
        if (taskKind == null || destroyed) {
            return false;
        }
        switch (type) {
            case WEB_EVENT_MEASUREMENT:
                requestMeasurement(payload);
                return true;
            case WEB_EVENT_LEVEL_START:
                if (taskKind != TaskKind.LEVEL) {
                    reportError(type, "当前页面不是找平任务");
                } else {
                    startLevel(payload);
                }
                return true;
            case WEB_EVENT_DITCH_START:
                if (taskKind != TaskKind.DITCH) {
                    reportError(type, "当前页面不是挖沟任务");
                } else {
                    startDitch(payload);
                }
                return true;
            case WEB_EVENT_SLOPE_START:
                if (taskKind != TaskKind.SLOPE) {
                    reportError(type, "当前页面不是修坡任务");
                } else {
                    startSlope(payload);
                }
                return true;
            default:
                return false;
        }
    }

    void destroy() {
        destroyed = true;
        if (taskKind == null || taskActivated) {
            return;
        }
        switch (taskKind) {
            case LEVEL:
                LevelTcuWorkflow level = LevelTcuWorkflow.getInstance();
                level.cancelPending();
                if (level.isFeatureActive()) {
                    level.exitFeature(null);
                } else {
                    level.resetLocal();
                }
                break;
            case DITCH:
                DitchTcuWorkflow ditch = DitchTcuWorkflow.getInstance();
                ditch.cancelPending();
                if (ditch.isFeatureActive()) {
                    ditch.exitFeature(null);
                } else {
                    ditch.resetLocal();
                }
                break;
            case SLOPE:
                SlopeRepairTcuWorkflow slope = SlopeRepairTcuWorkflow.getInstance();
                slope.cancelPending();
                if (slope.isFeatureActive()) {
                    slope.exitFeature(null);
                } else {
                    slope.resetLocal();
                }
                break;
        }
    }

    private void enterFeature() {
        switch (taskKind) {
            case LEVEL:
                LevelTcuWorkflow level = LevelTcuWorkflow.getInstance();
                if (level.isFeatureActive()) {
                    sendState(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK, level.getPhase().name());
                    return;
                }
                level.enterFeature(new LevelTcuWorkflow.StepCallback() {
                    @Override
                    public void onSuccess() {
                        sendState(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK, level.getPhase().name());
                    }

                    @Override
                    public void onError(String message) {
                        featureEnterRequested = false;
                        reportError("ENTER_FEATURE", message);
                    }
                });
                break;
            case DITCH:
                DitchTcuWorkflow ditch = DitchTcuWorkflow.getInstance();
                if (ditch.isFeatureActive()) {
                    sendState(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK, ditch.getPhase().name());
                    return;
                }
                ditch.enterFeature(new DitchTcuWorkflow.StepCallback() {
                    @Override
                    public void onSuccess() {
                        sendState(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK, ditch.getPhase().name());
                    }

                    @Override
                    public void onError(String message) {
                        featureEnterRequested = false;
                        reportError("ENTER_FEATURE", message);
                    }
                });
                break;
            case SLOPE:
                SlopeRepairTcuWorkflow slope = SlopeRepairTcuWorkflow.getInstance();
                if (slope.isFeatureActive()) {
                    sendState(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK, slope.getPhase().name());
                    return;
                }
                slope.enterFeature(new SlopeRepairTcuWorkflow.StepCallback() {
                    @Override
                    public void onSuccess() {
                        sendState(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK, slope.getPhase().name());
                    }

                    @Override
                    public void onError(String message) {
                        featureEnterRequested = false;
                        reportError("ENTER_FEATURE", message);
                    }
                });
                break;
        }
    }

    private void requestMeasurement(@Nullable JSONObject payload) {
        if (payload == null) {
            reportError(WEB_EVENT_MEASUREMENT, "测点参数缺失");
            return;
        }
        int featureId = payload.optInt("FeatureID", taskKind.featureId);
        if (featureId != taskKind.featureId) {
            reportError(WEB_EVENT_MEASUREMENT,
                    String.format(Locale.US, "FeatureID 应为 0x%02X", taskKind.featureId));
            return;
        }
        int pointMode = payload.optInt("PointMode", 1);
        int pointId = payload.optInt("PointID", defaultPointId(taskKind));

        switch (taskKind) {
            case LEVEL:
                if (pointId != TcuBusinessCodec.POINT_REF) {
                    reportError(WEB_EVENT_MEASUREMENT, "找平 PointID 必须为 0x00");
                    return;
                }
                LevelTcuWorkflow.getInstance().requestSurvey(pointMode,
                        new LevelTcuWorkflow.SurveyCallback() {
                            @Override
                            public void onSurveyResult(double heightM, double lat, double lon) {
                                sendMeasurement(pointId, pointMode, heightM, lat, lon);
                            }

                            @Override
                            public void onSuccess() {
                                sendState(TcuBusinessCodec.MSG_SURVEY_RESULT,
                                        LevelTcuWorkflow.getInstance().getPhase().name());
                            }

                            @Override
                            public void onError(String message) {
                                reportError(WEB_EVENT_MEASUREMENT, message);
                            }
                        });
                break;
            case DITCH:
                if (pointId != TcuBusinessCodec.POINT_A && pointId != TcuBusinessCodec.POINT_B) {
                    reportError(WEB_EVENT_MEASUREMENT, "挖沟 PointID 只能为 A(0x01) 或 B(0x02)");
                    return;
                }
                DitchTcuWorkflow.SurveyCallback ditchCallback =
                        new DitchTcuWorkflow.SurveyCallback() {
                            @Override
                            public void onSurveyResult(double heightM, double lat, double lon) {
                                sendMeasurement(pointId, pointMode, heightM, lat, lon);
                            }

                            @Override
                            public void onSuccess() {
                                sendState(TcuBusinessCodec.MSG_SURVEY_RESULT,
                                        DitchTcuWorkflow.getInstance().getPhase().name());
                            }

                            @Override
                            public void onError(String message) {
                                reportError(WEB_EVENT_MEASUREMENT, message);
                            }
                        };
                if (pointId == TcuBusinessCodec.POINT_A) {
                    DitchTcuWorkflow.getInstance().requestSurveyA(pointMode, ditchCallback);
                } else {
                    DitchTcuWorkflow.getInstance().requestSurveyB(pointMode, ditchCallback);
                }
                break;
            case SLOPE:
                if (pointId < TcuBusinessCodec.POINT_A || pointId > TcuBusinessCodec.POINT_C) {
                    reportError(WEB_EVENT_MEASUREMENT, "修坡 PointID 只能为 A/B/C");
                    return;
                }
                SlopeRepairTcuWorkflow.getInstance().requestSurvey(pointId, pointMode,
                        new SlopeRepairTcuWorkflow.SurveyCallback() {
                            @Override
                            public void onSurveyResult(double heightM, double lat, double lon) {
                                sendMeasurement(pointId, pointMode, heightM, lat, lon);
                            }

                            @Override
                            public void onSuccess() {
                                sendState(TcuBusinessCodec.MSG_SURVEY_RESULT,
                                        SlopeRepairTcuWorkflow.getInstance().getPhase().name());
                            }

                            @Override
                            public void onError(String message) {
                                reportError(WEB_EVENT_MEASUREMENT, message);
                            }
                        });
                break;
        }
    }

    private void startLevel(@Nullable JSONObject payload) {
        if (!beginStartRequest()) {
            return;
        }
        try {
            JSONObject result = requiredObject(payload, "levelTaskResult");
            int ref = parseBucketMode(result.optString("bucketPos", "MIDDLE"));
            boolean heightMode = !"COORDINATE".equalsIgnoreCase(
                    result.optString("currentFixationMode", "ALTITUDE"));
            double targetHeightM;
            if (heightMode) {
                targetHeightM = requiredDouble(result, "targetAltitude");
            } else {
                targetHeightM = LevelTaskState.getSurveyHeightM()
                        + requiredDouble(result, "digSize");
            }
            LevelTaskState.update(
                    ref,
                    heightMode,
                    numberText(targetHeightM - LevelTaskState.getSurveyHeightM()),
                    numberText(targetHeightM),
                    valueText(result, "targetLongitude"),
                    valueText(result, "targetLatitude"),
                    numberText(targetHeightM));

            LevelTcuWorkflow workflow = LevelTcuWorkflow.getInstance();
            workflow.submitLevelParams(targetHeightM, new LevelTcuWorkflow.StepCallback() {
                @Override
                public void onSuccess() {
                    sendState(TcuBusinessCodec.MSG_LEVEL_PARAMS_ACK, workflow.getPhase().name());
                    confirmLevelTask();
                }

                @Override
                public void onError(String message) {
                    startRequested = false;
                    reportError(WEB_EVENT_LEVEL_START, message);
                }
            });
        } catch (JSONException | IllegalArgumentException e) {
            startRequested = false;
            reportError(WEB_EVENT_LEVEL_START, e.getMessage());
        }
    }

    private void confirmLevelTask() {
        LevelTcuWorkflow workflow = LevelTcuWorkflow.getInstance();
        workflow.confirmTaskStart(new LevelTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                sendState(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK, workflow.getPhase().name());
                activateTask(TaskTypeState.Type.LEVEL);
            }

            @Override
            public void onError(String message) {
                startRequested = false;
                reportError(WEB_EVENT_LEVEL_START, message);
            }
        });
    }

    private void startDitch(@Nullable JSONObject payload) {
        if (!beginStartRequest()) {
            return;
        }
        try {

            JSONObject result = requiredObject(payload, "digTaskResult");
            WebPoint a = parsePoint(requiredObject(result, "PointAInfo"));
            WebPoint b = parsePoint(requiredObject(result, "PointBInfo"));
            int refA = parseBucketMode(result.optString("selectedAPointBucketTeeth", "MIDDLE"));
            int refB = parseBucketMode(result.optString("selectedBPointBucketTeeth", "MIDDLE"));
            int ditchType = "trapezoid".equalsIgnoreCase(
                    result.optString("digSelectedType", "square"))
                    ? DitchTaskState.DITCH_TRAPEZOID
                    : DitchTaskState.DITCH_SQUARE;

            DitchTaskState.reset();
            DitchTaskState.updateBase(
                    ditchType, refA, refB, valueText(result, "abPointDistance"));
            DitchTaskState.setHeightMode(false);
            DitchTaskState.setAbDistanceManual(true);
            DitchTaskState.updatePointA(
                    refA, "", "", numberText(a.lon), numberText(a.lat), numberText(a.heightM));
            DitchTaskState.updatePointB(
                    refB, "", "", numberText(b.lon), numberText(b.lat), numberText(b.heightM));
            DitchTaskState.updateSideParams(
                    valueText(result, "L_Width"),
                    valueText(result, "W_Width"),
                    valueText(result, "H_Height"),
                    valueText(result, "R_Width"));

            DitchTcuWorkflow workflow = DitchTcuWorkflow.getInstance();
            workflow.submitDitchParams(new DitchTcuWorkflow.StepCallback() {
                @Override
                public void onSuccess() {
                    sendState(TcuBusinessCodec.MSG_DITCH_PARAMS_ACK, workflow.getPhase().name());
                    confirmDitchTask();
                }

                @Override
                public void onError(String message) {
                    startRequested = false;
                    reportError(WEB_EVENT_DITCH_START, message);
                }
            });
        } catch (JSONException | IllegalArgumentException e) {
            startRequested = false;
            reportError(WEB_EVENT_DITCH_START, e.getMessage());
        }
    }

    private void confirmDitchTask() {
        DitchTcuWorkflow workflow = DitchTcuWorkflow.getInstance();
        workflow.confirmTaskStart(new DitchTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                sendState(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK, workflow.getPhase().name());
                activateTask(TaskTypeState.Type.DITCH);
            }

            @Override
            public void onError(String message) {
                startRequested = false;
                reportError(WEB_EVENT_DITCH_START, message);
            }
        });
    }

    private void startSlope(@Nullable JSONObject payload) {
        if (!beginStartRequest()) {
            return;
        }
        try {
            JSONObject result = requiredObject(payload, "repairSlopeResult");
            WebPoint a = parsePoint(requiredObject(result, "PointAInfo"));
            WebPoint b = parsePoint(requiredObject(result, "PointBInfo"));
            WebPoint c = parsePoint(requiredObject(result, "PointCInfo"));
            JSONObject section = requiredObject(result, "sectionParameter");
            int refA = parseBucketMode(result.optString("selectedAPointBucketTeeth", "MIDDLE"));
            int refB = parseBucketMode(result.optString("selectedBPointBucketTeeth", "MIDDLE"));
            int refC = parseBucketMode(result.optString("selectedCPointBucketTeeth", "MIDDLE"));
            double vertical = requiredDouble(section, "H_Width");
            double horizontal = requiredDouble(section, "L_Width");
            if (vertical == 0.0) {
                throw new IllegalArgumentException("修坡垂高 H 不能为 0");
            }
            double abDistance = optionalDouble(result, "abPointDistance",
                    optionalDouble(section, "AB_Width",
                            TcuBusinessCodec.horizontalDistanceM(a.lat, a.lon, b.lat, b.lon)));
            if (abDistance <= 0.0) {
                abDistance = TcuBusinessCodec.horizontalDistanceM(a.lat, a.lon, b.lat, b.lon);
            }
            double abLift = b.heightM - a.heightM;

            SlopeRepairTaskState.reset();
            SlopeRepairTaskState.updateRepairType(
                    "bottom".equalsIgnoreCase(result.optString("repairSlopeSelectedType", "top"))
                            ? SlopeRepairTaskState.TYPE_BOTTOM_LINE
                            : SlopeRepairTaskState.TYPE_TOP_LINE);
            SlopeRepairTaskState.setHeightMode(false);
            SlopeRepairTaskState.updatePointA(
                    refA, "", "", numberText(a.lon), numberText(a.lat), numberText(a.heightM));
            SlopeRepairTaskState.updatePointB(
                    refB, "", "", numberText(b.lon), numberText(b.lat), numberText(b.heightM));
            SlopeRepairTaskState.updateSurvey(
                    TcuBusinessCodec.POINT_C,
                    TcuBusinessCodec.metersToTenthCm(c.heightM),
                    c.lat,
                    c.lon);
            SlopeRepairTaskState.updateSecondStep(
                    refA, refB, numberText(abDistance), numberText(abLift),
                    numberText(Math.abs(abLift)));
            SlopeRepairTaskState.updateThirdStep(
                    refC,
                    numberText(horizontal / vertical),
                    numberText(vertical),
                    numberText(horizontal),
                    !"LEFT".equalsIgnoreCase(section.optString("SLOPE_TYPE", "RIGHT")));

            SlopeRepairTcuWorkflow workflow = SlopeRepairTcuWorkflow.getInstance();
            workflow.submitSlopeParams(new SlopeRepairTcuWorkflow.StepCallback() {
                @Override
                public void onSuccess() {
                    sendState(TcuBusinessCodec.MSG_SLOPE_PARAMS_ACK, workflow.getPhase().name());
                    confirmSlopeTask();
                }

                @Override
                public void onError(String message) {
                    startRequested = false;
                    reportError(WEB_EVENT_SLOPE_START, message);
                }
            });
        } catch (JSONException | IllegalArgumentException e) {
            startRequested = false;
            reportError(WEB_EVENT_SLOPE_START, e.getMessage());
        }
    }

    private void confirmSlopeTask() {
        SlopeRepairTcuWorkflow workflow = SlopeRepairTcuWorkflow.getInstance();
        workflow.confirmTaskStart(new SlopeRepairTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                sendState(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK, workflow.getPhase().name());
                activateTask(TaskTypeState.Type.SLOPE);
            }

            @Override
            public void onError(String message) {
                startRequested = false;
                reportError(WEB_EVENT_SLOPE_START, message);
            }
        });
    }

    private boolean beginStartRequest() {
        if (startRequested) {
            reportError("TASK_START", "任务正在提交，请勿重复操作");
            return false;
        }
        if (!isFeatureActive()) {
            reportError("TASK_START", "TCU 尚未确认进入当前功能");
            return false;
        }
        startRequested = true;
        return true;
    }

    private boolean isFeatureActive() {
        switch (taskKind) {
            case LEVEL:
                return LevelTcuWorkflow.getInstance().isFeatureActive();
            case DITCH:
                return DitchTcuWorkflow.getInstance().isFeatureActive();
            case SLOPE:
                return SlopeRepairTcuWorkflow.getInstance().isFeatureActive();
            default:
                return false;
        }
    }

    private void activateTask(TaskTypeState.Type type) {
        taskActivated = true;
        TaskTypeState.getInstance().setType(type);
        WorkRunState.getInstance().setState(WorkRunState.State.RUNNING);
        host.onTaskActivated(type);
    }

    private void sendMeasurement(
            int pointId, int pointMode, double heightM, double lat, double lon) {
        try {
            JSONObject payload = new JSONObject()
                    .put("MsgID", TcuBusinessCodec.MSG_SURVEY_RESULT)
                    .put("FeatureID", taskKind.featureId)
                    .put("PointID", pointId)
                    .put("PointMode", pointMode)
                    .put("height", heightM)
                    .put("latitude", lat)
                    .put("longitude", lon);
            sendEvent(NATIVE_EVENT_MEASUREMENT, payload);
        } catch (JSONException e) {
            Log.w(TAG, "build measurement event failed", e);
        }
    }

    private void sendState(int ackMsgId, String phase) {
        if (destroyed) {
            return;
        }
        try {
            JSONObject payload = new JSONObject()
                    .put("MsgID", ackMsgId)
                    .put("FeatureID", taskKind.featureId)
                    .put("phase", phase);
            if (ackMsgId == TcuBusinessCodec.MSG_FEATURE_SELECT_ACK) {
                payload.put("ActiveFeature", taskKind.featureId);
            } else if (ackMsgId == TcuBusinessCodec.MSG_LEVEL_PARAMS_ACK
                    && LevelTaskState.hasAcceptedTargetHeight()) {
                payload.put("targetHeight", LevelTaskState.getAcceptedTargetHeightM());
            } else if (ackMsgId == TcuBusinessCodec.MSG_TASK_CONFIRM_ACK) {
                payload.put("TaskState", 0x01);
            }
            sendEvent(NATIVE_EVENT_WORKFLOW_STATE, payload);
        } catch (JSONException e) {
            Log.w(TAG, "build workflow state event failed", e);
        }
    }

    private void reportError(String operation, @Nullable String message) {
        String safeMessage = message == null || message.trim().isEmpty() ? "任务参数无效" : message;
        Log.w(TAG, operation + ": " + safeMessage);
        try {
            sendEvent(NATIVE_EVENT_WORKFLOW_ERROR, new JSONObject()
                    .put("operation", operation)
                    .put("FeatureID", taskKind == null ? 0 : taskKind.featureId)
                    .put("message", safeMessage));
        } catch (JSONException e) {
            Log.w(TAG, "build workflow error event failed", e);
        }
        host.showError(safeMessage);
    }

    private void sendEvent(String type, JSONObject payload) throws JSONException {
        if (destroyed) {
            return;
        }
        host.sendToWeb(new JSONObject()
                .put("type", type)
                .put("payload", payload)
                .put("source", "native")
                .toString());
    }

    @Nullable
    private static TaskKind taskKindForRoute(@Nullable String route) {
        if (route == null) {
            return null;
        }
        if (route.contains("leveling-task")) {
            return TaskKind.LEVEL;
        }
        if (route.contains("dig-task")) {
            return TaskKind.DITCH;
        }
        if (route.contains("repair-slope")) {
            return TaskKind.SLOPE;
        }
        return null;
    }

    private static int defaultPointId(TaskKind kind) {
        return kind == TaskKind.LEVEL ? TcuBusinessCodec.POINT_REF : TcuBusinessCodec.POINT_A;
    }

    private static JSONObject requiredObject(@Nullable JSONObject parent, String key)
            throws JSONException {
        if (parent == null) {
            throw new JSONException("payload 缺失");
        }
        JSONObject value = parent.optJSONObject(key);
        if (value == null) {
            throw new JSONException(key + " 缺失");
        }
        return value;
    }

    /** 挖沟当前 payload：PointAInfo / PointBInfo 本身就是点对象。 */
    private static WebPoint parsePoint(JSONObject point) throws JSONException {
        return new WebPoint(
                requiredDouble(point, "latitude"),
                requiredDouble(point, "longitude"),
                requiredDouble(point, "height"));
    }
    private static double requiredDouble(JSONObject object, String key) throws JSONException {
        Object raw = object.opt(key);
        if (raw == null || raw == JSONObject.NULL) {
            throw new JSONException(key + " 缺失");
        }
        try {
            double value = raw instanceof Number
                    ? ((Number) raw).doubleValue()
                    : Double.parseDouble(String.valueOf(raw).trim().replace('−', '-'));
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new JSONException(key + " 不是有效数字");
        }
    }

    private static double optionalDouble(JSONObject object, String key, double fallback) {
        try {
            return requiredDouble(object, key);
        } catch (JSONException ignored) {
            return fallback;
        }
    }

    private static String valueText(JSONObject object, String key) {
        Object value = object.opt(key);
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value).trim();
    }

    private static String numberText(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static int parseBucketMode(String value) {
        if ("LEFT".equalsIgnoreCase(value)) {
            return LevelTaskState.REF_LEFT;
        }
        if ("RIGHT".equalsIgnoreCase(value)) {
            return LevelTaskState.REF_RIGHT;
        }
        return LevelTaskState.REF_MIDDLE;
    }

    private static final class WebPoint {
        final double lat;
        final double lon;
        final double heightM;

        WebPoint(double lat, double lon, double heightM) {
            this.lat = lat;
            this.lon = lon;
            this.heightM = heightM;
        }
    }
}
