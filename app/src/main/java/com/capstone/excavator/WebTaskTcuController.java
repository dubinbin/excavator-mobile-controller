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
    // 测点，webview模块向tcu发请求
    static final String WEB_EVENT_READY = "WEBVIEW_READY";
    static final String WEB_EVENT_MEASUREMENT = "MEASUREMENT_POINT_CALL";
    static final String WEB_EVENT_LEVEL_START = "LEVEL_TASK_START";
    static final String WEB_EVENT_DITCH_START = "DIG_TASK_START";
    static final String WEB_EVENT_SLOPE_START = "REPAIR_SLOPE_START";
    static final String WEB_EVENT_GET_LEVEL_CALC_DIG_AMOUNT_SIGNAL = "GET_LEVEL_CALC_DIG_AMOUNT_SIGNAL";

    private static final String NATIVE_EVENT_MEASUREMENT = "MEASUREMENT_POINT_RECEIVE";
    private static final String NATIVE_EVENT_WORKFLOW_STATE = "TCU_WORKFLOW_STATE";
    private static final String NATIVE_EVENT_WORKFLOW_ERROR = "TCU_WORKFLOW_ERROR";
    private static final String NATIVE_RECEIVE_LEVEL_CALC_DIG_AMOUNT = "RECEIVE_LEVEL_CALC_DIG_AMOUNT";

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
        // 每次任务页就绪都向 TCU 发送 0x04，并以其返回的 0x84 ActiveFeature 为准。
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
            case WEB_EVENT_GET_LEVEL_CALC_DIG_AMOUNT_SIGNAL:
                calcRealTimeDigAmount(payload);
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
                }
                break;
            case DITCH:
                DitchTcuWorkflow ditch = DitchTcuWorkflow.getInstance();
                ditch.cancelPending();
                if (ditch.isFeatureActive()) {
                    ditch.exitFeature(null);
                }
                break;
            case SLOPE:
                SlopeRepairTcuWorkflow slope = SlopeRepairTcuWorkflow.getInstance();
                slope.cancelPending();
                if (slope.isFeatureActive()) {
                    slope.exitFeature(null);
                }
                break;
        }
    }

    private void enterFeature() {
        switch (taskKind) {
            case LEVEL:
                LevelTcuWorkflow level = LevelTcuWorkflow.getInstance();
                level.enterFeature(new LevelTcuWorkflow.StepCallback() {
                    @Override
                    public void onSuccess() {
                        sendStateToWebView(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK, level.getPhase().name());
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
                ditch.enterFeature(new DitchTcuWorkflow.StepCallback() {
                    @Override
                    public void onSuccess() {
                        sendStateToWebView(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK, ditch.getPhase().name());
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
                slope.enterFeature(new SlopeRepairTcuWorkflow.StepCallback() {
                    @Override
                    public void onSuccess() {
                        sendStateToWebView(TcuBusinessCodec.MSG_FEATURE_SELECT_ACK, slope.getPhase().name());
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

    private void calcRealTimeDigAmount(@Nullable JSONObject payload) {
        if (payload == null) {
            reportError(WEB_EVENT_MEASUREMENT, "填挖量参数缺失");
            return;
        }
        int pointMode = payload.optInt("mode", 1); // 当前所选模式，0是高度定点模式，1是坐标定点模式
        String targetHeight = payload.optString("targetHeight"); // 目标高度
        String digMagnitude = payload.optString("digMagnitude"); // 填挖量
        String targetLongitude = payload.optString("targetLongitude"); // 目标经度
        String targetLatitude = payload.optString("targetLatitude"); // 目标纬度

        GlobalStatus.ImuAngles currentExcavatorInfo =
                GlobalStatus.getInstance().getRunTimeImuData();
        // TODO
        //currentExcavatorInfo.bucketAngle

        double calcHeight = 0.02;
        this.sendToWebviewAfterCalc(calcHeight);
    }

    // 把计算后的相对高度发给前端
    private void sendToWebviewAfterCalc(double height) {
        try {
            JSONObject payload = new JSONObject()
                    .put("MsgID", TcuBusinessCodec.MSG_SURVEY_RESULT)
                    .put("FeatureID", taskKind.featureId)
                    .put("height", height);
            sendEvent(NATIVE_RECEIVE_LEVEL_CALC_DIG_AMOUNT, payload);
        } catch (JSONException e) {
            Log.w(TAG, "build measurement event failed", e);
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
                //step1 参考点点击后，webview -> 到这里处理，对应6.2.4. TCU 回传测点结果帧，`MsgID = 0x90`，返回该点高度以及经纬度。
                LevelTcuWorkflow.getInstance().requestSurvey(pointMode,
                        new LevelTcuWorkflow.SurveyCallback() {
                            // 返回该点高度以及经纬度。
                            @Override
                            public void onSurveyResult(double heightM, double lat, double lon) {
                                sendMeasurement(pointId, pointMode, heightM, lat, lon);
                            }

                            @Override
                            public void onSuccess() {}

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
                            public void onSuccess() {}

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
                            public void onSuccess() {}

                            @Override
                            public void onError(String message) {
                                reportError(WEB_EVENT_MEASUREMENT, message);
                            }
                        });
                break;
        }
    }

    private void confirmLevelTask() {
        LevelTcuWorkflow workflow = LevelTcuWorkflow.getInstance();
        workflow.confirmTaskStart(new LevelTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                // 后续改一下，这个发到前端，前端才把页面关闭
                sendStateToWebView(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK, workflow.getPhase().name());
                activateTaskAndStartRealtimeGuidance(TaskTypeState.Type.LEVEL);
            }

            @Override
            public void onError(String message) {
                startRequested = false;
                reportError(WEB_EVENT_LEVEL_START, message);
            }
        });
    }


    private void startLevel(@Nullable JSONObject payload) {
        if (!beginStartRequest()) {
            return;
        }

        try {
            JSONObject result = requiredObject(payload, "levelTaskResult");

            // {
            //     "bucketPos": "MIDDLE", // 参考点
            //     "currentReferencePoint": "0", // 当前参考点 m
            //     "targetAltitude": "0", // 目标高度 m
            //     "digSize": "0.8", // 挖掘量 m
            //     "currentLongitudeAndLatitude": "0,0",// 经纬度
            //     "targetLongitude": "114.52", // 经度
            //     "targetLatitude": "22.55", // 纬度
            //     "currentFixationMode": "COORDINATE" // 定点方式 ALTITUDE 高度， COORDINATE经纬度
            // }

            int referencePoint = parseReferencePoint(
                    result.optString("bucketPos", "MIDDLE"));
            boolean heightMode = !"COORDINATE".equalsIgnoreCase(
                    result.optString("currentFixationMode", "ALTITUDE"));
            double currentReferencePointM = optionalDouble(
                    result, "currentReferencePoint", Double.NaN);
            double targetAltitudeM = requiredDouble(result, "targetAltitude");
            double digSizeM = requiredDouble(result, "digSize");
            String currentLocation = result.optString(
                    "currentLongitudeAndLatitude", "");
            double targetLongitude = optionalDouble(
                    result, "targetLongitude", Double.NaN);
            double targetLatitude = optionalDouble(
                    result, "targetLatitude", Double.NaN);

            LevelTaskState.TaskParameters taskParameters =
                    new LevelTaskState.TaskParameters(
                            referencePoint,
                            heightMode,
                            currentReferencePointM,
                            targetAltitudeM,
                            digSizeM,
                            currentLocation,
                            targetLongitude,
                            targetLatitude);

            LevelTaskState.update(taskParameters);

            // 参数快照保存后继续走 TCU 参数下发；0xC0 确认成功才启动首页实时引导。
            // 左右偏离公式统一留在 MainActivity 的 REALTIME_GUIDANCE TODO 中实现。
            LevelTcuWorkflow workflow = LevelTcuWorkflow.getInstance();
            workflow.submitLevelParams(taskParameters,
                    new LevelTcuWorkflow.StepCallback() {
                        @Override
                        public void onSuccess() {
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

    private void startDitch(@Nullable JSONObject payload) {
        if (!beginStartRequest()) {
            return;
        }
        try {

            JSONObject result = requiredObject(payload, "digTaskResult");

            // {
            //     "PointAInfo": {
            //         "height": "0.8",
            //         "longitude": "114.58",
            //         "latitude": "22.85"
            //     },
            //     "PointBInfo": {
            //         "height": "0.9",
            //         "longitude": "114.85",
            //         "latitude": "22.85"
            //     },
            //     "digSelectedType": "square" // trapezoid,
            //     "abPointDistance": 4,
            //     "L_Width": 0.8,
            //     "R_Width": 7.5,
            //     "W_Width": 0.3,
            //     "H_Height": 0.4
            // }

            WebPoint ditchPointA = parsePoint(requiredObject(result, "PointAInfo"));
            WebPoint ditchPointB = parsePoint(requiredObject(result, "PointBInfo"));
            int ditchType = "trapezoid".equalsIgnoreCase(
                    result.optString("digSelectedType", "square"))
                    ? DitchTcuWorkflow.DITCH_TRAPEZOID
                    : DitchTcuWorkflow.DITCH_SQUARE;
            double depthM = requiredDouble(result, "H_Height");
            double leftWidthM = requiredDouble(result, "L_Width");
            double rightWidthM = requiredDouble(result, "R_Width");
            double topWidthM = optionalDouble(result, "W_Width", 0.0);
            double abDistanceM = requiredDouble(result, "abPointDistance");

            DitchTaskState.TaskParameters taskParameters =
                    new DitchTaskState.TaskParameters(
                            ditchType,
                            new DitchTaskState.Point(ditchPointA.lat, ditchPointA.lon, ditchPointA.heightM),
                            new DitchTaskState.Point(ditchPointB.lat, ditchPointB.lon, ditchPointB.heightM),
                            abDistanceM,
                            depthM,
                            leftWidthM,
                            rightWidthM,
                            topWidthM);

            DitchTaskState.update(taskParameters);

            DitchTcuWorkflow workflow = DitchTcuWorkflow.getInstance();
            workflow.submitDitchParams(taskParameters,
                    new DitchTcuWorkflow.StepCallback() {
                        @Override
                        public void onSuccess() {
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


    private void startSlope(@Nullable JSONObject payload) {
        if (!beginStartRequest()) {
            return;
        }
        try {
            JSONObject result = requiredObject(payload, "repairSlopeResult");
            // {
            //     "PointAInfo": {
            //         "height": "8",
            //         "longitude": "4",
            //         "latitude": "8"
            //     },
            //     "PointBInfo": {
            //         "height": "5",
            //         "longitude": "7",
            //         "latitude": "88"
            //     },
            //     "PointCInfo": {
            //         "height": "0.8",
            //         "longitude": "2",
            //         "latitude": "8"
            //     },
            //     "repairSlopeSelectedType: "bottom" // top
            //     "abPointDistance": 3,
            //     "abHeightDifference": 1.5,
            //     "slopeRatio": 2,
            //     "verticalHeight": 1.5,
            //     "horizontalDistance": 2,
            //     "slopeAngle": 60,
            //     "sectionParameter": {
            //         "AB_Width": "2",
            //         "H_Width": "3",
            //         "L_Width": "3",
            //         "SLOPE_TYPE": "RIGHT"
            //     }
            // }
            WebPoint a = parsePoint(requiredObject(result, "PointAInfo"));
            WebPoint b = parsePoint(requiredObject(result, "PointBInfo"));
            WebPoint c = parsePoint(requiredObject(result, "PointCInfo"));
            JSONObject section = requiredObject(result, "sectionParameter");
            double abDistanceM = requiredDouble(result, "abPointDistance"); // ab距离
            double abHeightDifferenceM = requiredDouble(result, "abHeightDifference"); // AB高差
            double slopeRatio = requiredDouble(result, "slopeRatio"); // 坡比
            double verticalHeightM = requiredDouble(result, "verticalHeight"); // 垂高
            double slopeAngle = requiredDouble(result, "slopeAngle"); // 坡角
            double horizontalDistanceM = requiredDouble(result, "horizontalDistance"); // 平距


            int repairType = "bottom".equalsIgnoreCase(
                    result.optString("repairSlopeSelectedType", "top"))
                    ? SlopeRepairTcuWorkflow.TYPE_BOTTOM_LINE
                    : SlopeRepairTcuWorkflow.TYPE_TOP_LINE;
            String slopeDirection = section.optString("SLOPE_TYPE", "");

            SlopeRepairTaskState.TaskParameters taskParameters =
                    new SlopeRepairTaskState.TaskParameters(
                            repairType,
                            new SlopeRepairTaskState.Point(a.lat, a.lon, a.heightM),
                            new SlopeRepairTaskState.Point(b.lat, b.lon, b.heightM),
                            new SlopeRepairTaskState.Point(c.lat, c.lon, c.heightM),
                            verticalHeightM,
                            horizontalDistanceM,
                            abDistanceM,
                            abHeightDifferenceM,
                            slopeRatio,
                            slopeAngle,
                            slopeDirection);

            SlopeRepairTaskState.update(taskParameters);

            SlopeRepairTcuWorkflow workflow = SlopeRepairTcuWorkflow.getInstance();
            workflow.submitSlopeParams(taskParameters, new SlopeRepairTcuWorkflow.StepCallback() {
                @Override
                public void onSuccess() {
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

    private void confirmDitchTask() {
        DitchTcuWorkflow workflow = DitchTcuWorkflow.getInstance();
        workflow.confirmTaskStart(new DitchTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                // 后续改一下，这个发到前端，前端才把页面关闭
                sendStateToWebView(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK, workflow.getPhase().name());
                activateTaskAndStartRealtimeGuidance(TaskTypeState.Type.DITCH);
            }

            @Override
            public void onError(String message) {
                startRequested = false;
                reportError(WEB_EVENT_DITCH_START, message);
            }
        });
    }

    private void confirmSlopeTask() {
        SlopeRepairTcuWorkflow workflow = SlopeRepairTcuWorkflow.getInstance();
        workflow.confirmTaskStart(new SlopeRepairTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                // 后续改一下，这个发到前端，前端才把页面关闭
                sendStateToWebView(TcuBusinessCodec.MSG_TASK_CONFIRM_ACK, workflow.getPhase().name());
                activateTaskAndStartRealtimeGuidance(TaskTypeState.Type.SLOPE);
            }

            @Override
            public void onError(String message) {
                startRequested = false;
                reportError(WEB_EVENT_SLOPE_START, message);
            }
        });
    }

    /**
     * [实时引导启动事件]
     * confirmTaskStart 成功后发布任务类型和 RUNNING 状态，并通知 Host 返回首页。
     * MainActivity 随后读取 GlobalStatus 最新 IMU，后续每帧 IMU 更新继续驱动本地计算。
     */
    private void activateTaskAndStartRealtimeGuidance(TaskTypeState.Type type) {
        taskActivated = true;
        TaskTypeState.getInstance().setType(type);
        WorkRunState.getInstance().setState(WorkRunState.State.RUNNING);
        host.onTaskActivated(type);
    }


    // 发送测点请求到webview
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


    // 将 TCU Workflow 应答状态通知给 WebView，但是似乎并不需要通知webview
    private void sendStateToWebView(int ackMsgId, String phase) {
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

    private static int parseReferencePoint(String bucketPosition) {
        if ("LEFT".equalsIgnoreCase(bucketPosition)) {
            return LevelTaskState.REF_LEFT;
        }
        if ("RIGHT".equalsIgnoreCase(bucketPosition)) {
            return LevelTaskState.REF_RIGHT;
        }
        return LevelTaskState.REF_MIDDLE;
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
