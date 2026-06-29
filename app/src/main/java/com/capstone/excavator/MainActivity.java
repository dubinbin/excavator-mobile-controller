package com.capstone.excavator;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;

import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.skydroid.rcsdk.RCSDKManager;
import com.skydroid.rcsdk.KeyManager;
import com.skydroid.rcsdk.comm.CommListener;
import com.skydroid.rcsdk.common.pipeline.Pipeline;
import com.skydroid.rcsdk.PipelineManager;
import com.skydroid.rcsdk.common.error.SkyException;
import com.skydroid.rcsdk.SDKManagerCallBack;
import com.skydroid.rcsdk.key.RemoteControllerKey;
import com.skydroid.rcsdk.key.AirLinkKey;
import com.skydroid.rcsdk.common.callback.KeyListener;
import com.skydroid.rcsdk.common.callback.CompletionCallbackWith;

import com.skydroid.fpvplayer.FPVWidget;
import com.skydroid.fpvplayer.OnPlayerStateListener;
import com.skydroid.fpvplayer.PlayerType;
import com.skydroid.fpvplayer.RtspTransport;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends ScaledAppCompatActivity {
    
    // ── Components ───────────────────────────────────────────────────
    private HeaderBarView headerBar;
    private BottomBarView bottomBar;
    // ── Other UI ─────────────────────────────────────────────────────
    private PostureCardView postureCardView;
    private BlurView postureCardBlur;
    private BlurView riseSpeedBlur;
    private FPVWidget fpvWidget;
    private ExcavatorMapWeb mapView;
    private View mapCardContainer;
    private BlurView rightPanelBlur;
    private View videoPlaceholder;
    private View noLiveVideoOverlay;
    private View rightPanelHeader;
    private View rightPanelBody;
    private View rightPanelCollapseArrow;
    private BlurView livePillBlur;
    private View referencePointTitleBar;
    private View centerActivityPanelView;
    private View centerCapsuleSpeedDirectionContainer;
    private View leftActivityPanel;
    private View rightActivityPanel;
    private View verticalActivityPanelLeft;
    private View verticalActivityPanelRight;
    private TextView joystickRawDebugText;
    private final LevelTaskGuidanceController levelTaskGuidance = new LevelTaskGuidanceController();
    private MotionModeSegmentView motionModeSegment;
    private volatile int desiredMotionModeChannelIndex = MotionModeSegmentView.INDEX_STOP;
    private volatile ControllerLocalSettings.Snapshot currentJoystickUiMappingSnapshot;
    private final AtomicInteger motionModeChannelApplyGeneration = new AtomicInteger();
    private EmergencyStopOverlayView emergencyStopOverlay;
    private ConfirmDialogView confirmDialog;
    private InlineToastView inlineToast;
    private ExcavatorWebAppPreloader webAppPreloader;

    private final TaskTypeState.OnTypeChangeListener taskTypeListener =
            (newType, oldType) -> runOnUiThread(this::applyTaskOverlayVisibility);
    private final WorkRunState.OnStateChangeListener workStateListener =
            (newState, oldState) -> runOnUiThread(this::applyTaskOverlayVisibility);

    // 数据更新Handler
    private Handler handler;
    private Runnable updateRunnable;
    
    // 摇杆值更新Handler（独立更新）
    private Handler joystickHandler;
    private Runnable joystickUpdateRunnable;
    private volatile boolean remoteControllerConnected;
    private final ImuAngleConverter.Config imuAngleConfig = ImuAngleConverter.createDefaultConfig();
    
    // UDP相关
    private Pipeline udpPipeline;
    /** 是否已向顶栏报告接收机链路可用（避免每包 runOnUiThread）。 */
    private boolean receiverLinkAlive;
    private boolean useRealData = false; // 是否使用真实UDP数据
    private float realBoomAngle = 0f;
    private float realStickAngle = 0f;
    private float realBucketAngle = 0f;
    private float realCabinPitchAngle = 0f;
    private float realCabinRollAngle = 0f;
    // RTK?????????
    private double realRtkLat = 0.0;
    private double realRtkLon = 0.0;

    /** 与 {@link #updatePositioning()} 模拟 RTK 一致，避免 initMap 与首秒定时器注入坐标不一致。 */
    private static final double SIM_RTK_DEFAULT_LAT = 28.2416021;
    private static final double SIM_RTK_DEFAULT_LON = 113.0938459;

    // UDP数据接收超时相关
    private Handler udpTimeoutHandler;
    private Runnable udpTimeoutRunnable;
    private static final long UDP_TIMEOUT_MS = 5000; // 5秒没收到数据就切换回模拟数据

    private long lastDataReceiveTime = 0;
    /** 最近一次成功解析的 {@code 0xFA} IMU 帧，供 {@code 0x51}/{@code 0x50} 到达后重渲染顶栏 IMU 颜色 */
    private IMUDataParser.ParsedData lastUdpImuParsedSnapshot;
    // 心跳 RTT 测量相关
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;
    private static final long HEARTBEAT_INTERVAL_MS = 1000; // 心跳间隔 1 秒
    // 心跳帧格式：0xFB 0xFB + 1字节类型(0x01) + 8字节时间戳 + 19字节填充 + CRC(2字节) + 0xFF = 33字节
    private static final byte HEARTBEAT_HEADER_1 = (byte) 0xFB;
    private static final byte HEARTBEAT_HEADER_2 = (byte) 0xFB;
    private static final byte HEARTBEAT_TYPE     = (byte) 0x01;
    // 摇杆值
    private int ch1Value = 0; // 右摇杆左右
    private int ch2Value = 0; // 右摇杆上下
    private int ch3Value = 0; // 左摇杆上下
    private int ch4Value = 0; // 左摇杆左右
    private int ch5Value = 0; // 模式切换

    // 信号强度相关
    private KeyListener<Integer> keySignalQualityListener;
    private int currentSignalStrength = 0; // 当前信号强度（0-100）
    
    // 视频流地址（默认；持久化见 {@link ControllerLocalSettings}）
    private static final String DEFAULT_VIDEO_URL = "rtsp://192.168.144.25:8554/main.264";
    private String currentVideoUrl = DEFAULT_VIDEO_URL;
    private static final int REQUEST_SETTINGS = 1001;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // 设置全屏模式
        setFullScreenMode();
        setContentView(R.layout.activity_main);
        ExcavatorWebAppBridge.setMessageListener(this::showWebAppBridgeMessage);
        webAppPreloader = ExcavatorWebAppPreloader.preload(this);
        inflateFpvVideoLayerUnscaled();
        initViews();
        initMap();
        initImuAngleConfig();
        applyStoredArmLengthScalesToWebView();
        initSDK();
        startDataUpdates();
        initVideoPlayer();
        showInitialPrompts();
    }

    @Override
    protected void onStart() {
        super.onStart();
        TaskTypeState.getInstance().addListener(taskTypeListener);
        WorkRunState.getInstance().addListener(workStateListener);
        applyTaskOverlayVisibility();
    }

    @Override
    protected void onStop() {
        TaskTypeState.getInstance().removeListener(taskTypeListener);
        WorkRunState.getInstance().removeListener(workStateListener);
        super.onStop();
    }

    /**
     * 首次 onResume 紧跟 {@link #onCreate} 的 {@link #initVideoPlayer()}（已经 start 过 fpv），
     * 跳过一次 start 避免双启动。后续 onResume 会重新 start。
     */
    private boolean fpvFirstResumeSkipped = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (!ExcavatorWebAppPreloader.hasAllWarmedViews()) {
            webAppPreloader = ExcavatorWebAppPreloader.preload(this);
        }
        if (mapView != null) mapView.resume();
        if (postureCardView != null) postureCardView.onActivityResume();
        if (!fpvFirstResumeSkipped) {
            fpvFirstResumeSkipped = true;
            // initVideoPlayer 在 onCreate 中已经 start 过，不再重复 start。
        } else if (fpvWidget != null) {
            try {
                fpvWidget.start();
            } catch (Throwable t) {
                Log.w("MainActivity", "fpvWidget.start onResume failed: " + t.getMessage());
            }
        }
        // UDP 已连且 onPause 时停过心跳：重新拉起。startHeartbeat 内部会幂等清理旧 runnable。
        if (udpPipeline != null && (udpPipeline.isConnected() || TcuLinkHub.isTrafficAlive())) {
            startHeartbeat();
            noteReceiverLinkAlive();
        } else {
            setReceiverLinkConnected(false);
        }
    }

    @Override
    protected void onPause() {
        // 1) FPV：解码器在 Activity 不可见时仍持有 GPU 表面会浪费功耗，且回到前台时常见黑屏 / 抖动。
        if (fpvWidget != null) {
            try {
                fpvWidget.stop();
            } catch (Throwable t) {
                Log.w("MainActivity", "fpvWidget.stop onPause failed: " + t.getMessage());
            }
        }
        // 2) WebView：天地图 / 姿态 WebView 的 requestAnimationFrame、JS 计时器在背景仍会跑。
        if (mapView != null) mapView.pause();
        if (postureCardView != null) postureCardView.onActivityPause();
        // 3) 心跳定时器
        stopHeartbeat();
        super.onPause();
    }

    /** 首次启动引导：先选语言，再提示是否配置机器参数。 */
    private void showInitialPrompts() {
        if (!LanguageManager.isLanguageChosen(this)) {
            LanguagePickerDialog.show(this, langCode -> {
                showFirstRunConfigIfNeeded();
            });
        } else {
            showFirstRunConfigIfNeeded();
        }
    }

    /** 首次运行时提示配置机器参数；跳过或配置后都不再自动弹出。 */
    private void showFirstRunConfigIfNeeded() {
        FirstRunConfigDialog.showIfNeeded(this, this::openGeneralSettingsPage);
    }

    private void openGeneralSettingsPage() {
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        intent.putExtra("current_url", currentVideoUrl);
        intent.putExtra(SettingsActivity.EXTRA_INITIAL_PAGE, SettingsActivity.PAGE_GENERAL);
        startActivityForResult(intent, REQUEST_SETTINGS);
    }
    
    /**
     * 设置全屏模式（隐藏状态栏和导航栏）
     */
    private void setFullScreenMode() {
        // 使用 WindowCompat 和 WindowInsetsControllerCompat 实现兼容性全屏
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        
        if (windowInsetsController != null) {
            // 隐藏状态栏和导航栏
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            // 设置沉浸式模式，让内容可以延伸到系统栏区域
            windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }

    }

    private void showWebAppBridgeMessage(String message) {
        Toast.makeText(this, "Web消息: " + message, Toast.LENGTH_LONG).show();
    }
    
    @Override
    public void onBackPressed() {
        if (emergencyStopOverlay != null
                && emergencyStopOverlay.getVisibility() == View.VISIBLE) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 当窗口获得焦点时，确保全屏模式
            setFullScreenMode();
        }
    }

    /**
     * 图传 subtree 使用「恢复物理 density」的 Context inflate，与全局 {@link UiScaleConfig#wrap} 解耦，
     * 减轻硬解 Surface 与 0.8× dpi 叠带来的测量/合成压力（其余 UI 仍走缩放 Activity）。
     */
    private void inflateFpvVideoLayerUnscaled() {
        ViewStub stub = findViewById(R.id.fpvLayerStub);
        if (stub == null) {
            Log.w("MainActivity", "fpvLayerStub missing; FPV layer not inflated");
            return;
        }
        Context inflaterCtx = UiScaleConfig.unscaledContext(this);
        stub.setLayoutInflater(LayoutInflater.from(inflaterCtx));
        try {
            stub.inflate();
        } catch (IllegalStateException e) {
            Log.w("MainActivity", "FPV layer already inflated: " + e.getMessage());
        }
    }

    private void initViews() {
        postureCardView = findViewById(R.id.postureCardView);
        postureCardBlur = findViewById(R.id.postureCardBlur);
        riseSpeedBlur = findViewById(R.id.riseSpeedBlur);
        fpvWidget            = findViewById(R.id.fpvWidget);
        View mapSlot = findViewById(R.id.mapView);
        mapView = (mapSlot instanceof ExcavatorMapWeb) ? (ExcavatorMapWeb) mapSlot : null;
        mapCardContainer     = findViewById(R.id.mapCardContainer);
        rightPanelBlur       = findViewById(R.id.rightPanelBlur);
        rightPanelHeader     = findViewById(R.id.rightPanelHeader);
        rightPanelBody       = findViewById(R.id.rightPanelBody);
        rightPanelCollapseArrow = findViewById(R.id.rightPanelCollapseArrow);
        videoPlaceholder     = findViewById(R.id.videoPlaceholder);
        noLiveVideoOverlay   = findViewById(R.id.noLiveVideoOverlay);
        livePillBlur         = findViewById(R.id.livePillBlur);
        referencePointTitleBar = findViewById(R.id.referencePointTitleBar);
        centerActivityPanelView = findViewById(R.id.centerActivityPanelView);
        centerCapsuleSpeedDirectionContainer = findViewById(R.id.centerCapsuleSpeedDirectionContainer);
        leftActivityPanel = findViewById(R.id.leftActivityPanel);
        rightActivityPanel = findViewById(R.id.rightActivityPanel);
        verticalActivityPanelLeft = findViewById(R.id.verticalActivityPanelLeft);
        verticalActivityPanelRight = findViewById(R.id.verticalActivityPanelRight);
        joystickRawDebugText = findViewById(R.id.joystickRawDebugText);
        updateJoystickRawDebugText();
        levelTaskGuidance.setImuAngleConfig(imuAngleConfig);
        levelTaskGuidance.bind(this);
        motionModeSegment = findViewById(R.id.motionModeSegment);
        if (motionModeSegment != null) {
            motionModeSegment.setOnIndexChangeListener(this::onMotionModeChanged);
        }

        // Sync right map card height to posture card height after layout.
        if (postureCardView != null && mapCardContainer != null) {
            postureCardView.post(() -> {
                int h = postureCardView.getHeight();
                if (h <= 0) return;
                ViewGroup.LayoutParams lp = mapCardContainer.getLayoutParams();
                if (lp != null && lp.height != h) {
                    lp.height = h;
                    mapCardContainer.setLayoutParams(lp);
                }
            });
        }

        setupOverlayBlurs();
        setupCollapsibleCards();

        // ── Header component ────────────────────────────────────────
        headerBar = findViewById(R.id.headerBar);
        headerBar.setMode("手动模式");
        setSensorStatusesOffline();

        // ── 急停覆盖层 ───────────────────────────────────────────────
        emergencyStopOverlay = findViewById(R.id.emergencyStopOverlay);
        headerBar.setOnEmergencyStopListener(() -> {
            if (emergencyStopOverlay != null) emergencyStopOverlay.show();
        });
        if (emergencyStopOverlay != null) {
            emergencyStopOverlay.setOnDismissListener(() ->
                    Toast.makeText(this, "急停已解除", Toast.LENGTH_SHORT).show());
        }

        // ── Bottom bar component ─────────────────────────────────────
        bottomBar = findViewById(R.id.bottomBar);

        bottomBar.setOnReconnectListener(this::reconnectVideo);

        bottomBar.setOnSettingsListener(() -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            intent.putExtra("current_url", currentVideoUrl);
            startActivityForResult(intent, REQUEST_SETTINGS);
        });

        bottomBar.setOnLevelListener(() -> {
            openWebAppRoute(ExcavatorWebAppView.ROUTE_LEVELING_TASK_STEP1);
        });
        

        bottomBar.setOnTrenchListener(() -> {
            openWebAppRoute(ExcavatorWebAppView.ROUTE_DIG_TASK_STEP1);
        });

        bottomBar.setOnSlopeListener(() -> {
            openWebAppRoute(ExcavatorWebAppView.ROUTE_REPAIR_SLOPE_STEP1);
        });

        // ── 通用弹窗 / Toast ─────────────────────────────────────────
        confirmDialog = findViewById(R.id.confirmDialog);
        inlineToast   = findViewById(R.id.inlineToast);

        // 「结束」按钮：弹二次确认弹窗
        bottomBar.setOnEndListener(() -> {
            if (confirmDialog == null) return;
            confirmDialog.show(new ConfirmDialogView.Config.Builder("确认退出当前任务?")
                    .subtitle("")
                    .confirmText("确认退出")
                    .cancelText("取消")
                    .onConfirm(() -> {
                        TaskTypeState.Type endingType = TaskTypeState.getInstance().getType();
                        clearTaskSessionOnEnd(endingType);
                        WorkRunState.getInstance().setState(WorkRunState.State.ENDED);
                        TaskTypeState.getInstance().setType(TaskTypeState.Type.NONE);
                        if (inlineToast != null) inlineToast.showMessage("任务已终止");
                    })
                    .build());
        });

        // 「暂停」按钮：在 RUNNING↔PAUSED 之间切换
        bottomBar.setOnPauseListener(() -> {
            WorkRunState.State cur = WorkRunState.getInstance().getState();
            if (cur == WorkRunState.State.RUNNING) {
                WorkRunState.getInstance().setState(WorkRunState.State.PAUSED);
            } else if (cur == WorkRunState.State.PAUSED) {
                WorkRunState.getInstance().setState(WorkRunState.State.RUNNING);
            }
        });

        // 初始状态：视频与接收机链路均未连接
        setVideoConnected(false);
        setReceiverLinkConnected(false);

        applyTaskOverlayVisibility();
    }

    private void openWebAppRoute(String route) {
        Intent intent = new Intent(this, ExcavatorWebAppActivity.class);
        intent.putExtra(ExcavatorWebAppActivity.EXTRA_INITIAL_ROUTE, route);
        startActivity(intent);
    }

    /**
     * 主页「结束任务」：退出 TCU 功能并清空本地任务态/引导快照，防止下次作业沿用上次数据。
     */
    private void clearTaskSessionOnEnd(TaskTypeState.Type endingType) {
        LevelTcuWorkflow levelWorkflow = LevelTcuWorkflow.getInstance();
        DitchTcuWorkflow ditchWorkflow = DitchTcuWorkflow.getInstance();
        SlopeRepairTcuWorkflow slopeWorkflow = SlopeRepairTcuWorkflow.getInstance();
        levelWorkflow.cancelPending();
        ditchWorkflow.cancelPending();
        slopeWorkflow.cancelPending();

        if (endingType == TaskTypeState.Type.LEVEL && levelWorkflow.isFeatureActive()) {
            levelWorkflow.exitFeature(null);
        } else {
            levelWorkflow.resetLocal();
        }
        if (endingType == TaskTypeState.Type.DITCH && ditchWorkflow.isFeatureActive()) {
            ditchWorkflow.exitFeature(null);
        } else {
            ditchWorkflow.resetLocal();
        }
        if (endingType == TaskTypeState.Type.SLOPE && slopeWorkflow.isFeatureActive()) {
            slopeWorkflow.exitFeature(null);
        } else {
            slopeWorkflow.resetLocal();
        }

        LevelTaskState.resetAll();
        DitchTaskState.reset();
        SlopeRepairTaskState.reset();
    }

    private void applyTaskOverlayVisibility() {
        TaskTypeState.Type taskType = TaskTypeState.getInstance().getType();
        WorkRunState.State workState = WorkRunState.getInstance().getState();
        boolean taskActive = taskType != TaskTypeState.Type.NONE
                && (workState == WorkRunState.State.RUNNING || workState == WorkRunState.State.PAUSED);
        boolean slopeTask = taskActive && taskType == TaskTypeState.Type.SLOPE;
        boolean ditchTask = taskActive && taskType == TaskTypeState.Type.DITCH;
        boolean levelOrDitchTask = taskActive
                && (taskType == TaskTypeState.Type.LEVEL || taskType == TaskTypeState.Type.DITCH);

        setVisible(riseSpeedBlur, slopeTask);
        setVisible(verticalActivityPanelLeft, slopeTask);
        setVisible(verticalActivityPanelRight, slopeTask);

        setVisible(leftActivityPanel, levelOrDitchTask);
        setVisible(rightActivityPanel, levelOrDitchTask);

        setVisible(centerActivityPanelView, ditchTask);
        setVisible(referencePointTitleBar, ditchTask);
        setVisible(centerCapsuleSpeedDirectionContainer, ditchTask);
        setTopMarginDp(livePillBlur, ditchTask ? 85f : 55f);
        levelTaskGuidance.applySpeedIndicatorOverlay(slopeTask, taskActive);

        boolean guidanceRunning = taskActive
                && workState == WorkRunState.State.RUNNING
                && (taskType == TaskTypeState.Type.LEVEL
                || taskType == TaskTypeState.Type.DITCH
                || taskType == TaskTypeState.Type.SLOPE);
        levelTaskGuidance.onGuidanceRunningChanged(guidanceRunning, taskType, useRealData);
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void setTopMarginDp(View view, float topMarginDp) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
        int topMarginPx = Math.round(topMarginDp * getResources().getDisplayMetrics().density);
        if (marginLayoutParams.topMargin == topMarginPx) {
            return;
        }
        marginLayoutParams.topMargin = topMarginPx;
        view.setLayoutParams(marginLayoutParams);
    }


    // 设置某些窗体为blur毛玻璃效果
    private void setupOverlayBlurs() {
        View t = findViewById(R.id.blurTarget);
        if (!(t instanceof BlurTarget)) return;
        BlurTarget target = (BlurTarget) t;

        final float cardRadius = 18f;
        final int cardOverlay = 0x4D808080; // glass gray overlay
        final float liveRadius = 14f;
        final int liveOverlay = 0xAA000000; // black blur overlay

        if (postureCardBlur != null) {
            postureCardBlur.post(() -> postureCardBlur.setupWith(target)
                    .setBlurRadius(cardRadius)
                    .setOverlayColor(cardOverlay));
        }
        if (rightPanelBlur != null) {
            rightPanelBlur.post(() -> rightPanelBlur.setupWith(target)
                    .setBlurRadius(cardRadius)
                    .setOverlayColor(cardOverlay));
        }
        if (livePillBlur != null) {
            livePillBlur.post(() -> livePillBlur.setupWith(target)
                    .setBlurRadius(liveRadius)
                    .setOverlayColor(liveOverlay));
        }
        if (riseSpeedBlur != null) {
            riseSpeedBlur.post(() -> riseSpeedBlur.setupWith(target)
                    .setBlurRadius(cardRadius)
                    .setOverlayColor(cardOverlay));
        }
    }

    private void setupCollapsibleCards() {
        // Left: PostureCardView internal header/body
        if (postureCardBlur != null && postureCardView != null) {
            View header = postureCardView.findViewById(R.id.postureHeaderRow);
            View body = postureCardView.findViewById(R.id.postureBody);
            View arrow = postureCardView.findViewById(R.id.postureCollapseArrow);
            setupOneCollapsible(postureCardBlur, header, body, arrow);
        }

        // Right panel
        if (rightPanelBlur != null && rightPanelHeader != null && rightPanelBody != null) {
            setupOneCollapsible(rightPanelBlur, rightPanelHeader, rightPanelBody, rightPanelCollapseArrow);
        }
    }

    private void setupOneCollapsible(View container, View header, View body, View arrow) {
        if (container == null || header == null || body == null) return;

        final boolean[] collapsed = { false };
        final int[] expandedH = { -1 };
        final int[] collapsedH = { -1 };

        container.post(() -> {
            expandedH[0] = container.getHeight();
            collapsedH[0] = header.getHeight();
        });

        Runnable expand = () -> {
            if (!collapsed[0]) return;
            collapsed[0] = false;
            body.setVisibility(View.VISIBLE);
            int from = container.getLayoutParams().height > 0 ? container.getLayoutParams().height : collapsedH[0];
            int to = expandedH[0] > 0 ? expandedH[0] : container.getHeight();
            animateHeight(container, from, to, () -> {});
            if (arrow != null) arrow.animate().rotation(0f).setDuration(180).start();
        };

        Runnable collapse = () -> {
            if (collapsed[0]) return;
            collapsed[0] = true;
            int from = container.getHeight();
            int to = collapsedH[0] > 0 ? collapsedH[0] : header.getHeight();
            animateHeight(container, from, to, () -> body.setVisibility(View.GONE));
            if (arrow != null) arrow.animate().rotation(180f).setDuration(180).start();
        };

        if (arrow != null) {
            arrow.setOnClickListener(v -> {
                if (collapsed[0]) expand.run();
                else collapse.run();
            });
        }

        header.setOnClickListener(v -> {
            if (collapsed[0]) expand.run();
        });
    }

    private void animateHeight(View v, int from, int to, Runnable endAction) {
        if (from == to) {
            if (endAction != null) endAction.run();
            return;
        }
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(220);
        animator.addUpdateListener(a -> {
            lp.height = (int) a.getAnimatedValue();
            v.setLayoutParams(lp);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (endAction != null) endAction.run();
            }
        });
        animator.start();
    }


    // 初始化地图
    private void initMap() {
        if (mapView == null) return;
        mapView.setFixedLocation(SIM_RTK_DEFAULT_LAT, SIM_RTK_DEFAULT_LON, 0.0);
    }
    
    /**
     * 初始化IMU解算配置
     */
    private void initImuAngleConfig() {
        imuAngleConfig.name = "cat303cr";
        imuAngleConfig.type = "cat303cr";

        ImuPreferences.Params p = ImuPreferences.load(this);

        ImuAngleConverter.Dimensions dim = imuAngleConfig.dimensions;
        dim.chassisWidth  = 0.0;
        dim.chassisLength = 0.0;
        dim.trackWidth    = 0.0;
        dim.boomLength           = p.boomLength;
        dim.stickLength          = p.stickLength;
        dim.bucketLength         = p.bucketLength;
        dim.bucketAngleOffsetDeg = p.bucketAngleOffsetDeg;

        ImuAngleConverter.CylinderJointMapDimensions cyl = imuAngleConfig.cylinder;
        cyl.boomL2 = p.boomL2;
        cyl.boomL3 = p.boomL3;
        cyl.boomL4 = p.boomL4;
        cyl.boomL5 = p.boomL5;
        cyl.boomL6 = p.boomL6;
        cyl.boomL7 = p.boomL7;

        cyl.stickL2 = p.stickL2;
        cyl.stickL3 = p.stickL3;
        cyl.stickL4 = p.stickL4;
        cyl.stickL5 = p.stickL5;
        cyl.stickL6 = p.stickL6;
        cyl.stickL7 = p.stickL7;

        cyl.bucketL2  = p.bucketL2;
        cyl.bucketL3  = p.bucketL3;
        cyl.bucketL4  = p.bucketL4;
        cyl.bucketL5  = p.bucketL5;
        cyl.bucketL6  = p.bucketL6;
        cyl.bucketL7  = p.bucketL7;
        cyl.bucketL9  = p.bucketL9;
        cyl.bucketL10 = p.bucketL10;

        ImuAngleConverter.ImuInstallationOffset offsets = imuAngleConfig.imuOffsets;
        offsets.boomImuOffsetDeg   = p.boomImuOffsetDeg;
        offsets.stickImuOffsetDeg  = p.stickImuOffsetDeg;
        offsets.bucketImuOffsetDeg = p.bucketImuOffsetDeg;

        if (postureCardView != null) {
            postureCardView.setBucketAngleOffsetDeg((float) dim.bucketAngleOffsetDeg);
        }
        levelTaskGuidance.setImuAngleConfig(imuAngleConfig);
    }

    /** 从 SharedPreferences 恢复臂长比例，WebView onPageFinished 后会随 payload 下发。 */
    private void applyStoredArmLengthScalesToWebView() {
        if (postureCardView == null) {
            return;
        }
        float boom = ArmLengthPreferences.getBoomScale(this);
        float stick = ArmLengthPreferences.getStickScale(this);
        postureCardView.setLengthScales(boom, stick);
    }

    /**
     * 初始化视频播放器
     */
    private void initVideoPlayer() {
        if (fpvWidget == null) return;

        ControllerLocalSettings.Snapshot snap = ControllerLocalSettings.load(this);
        if (snap.videoStreamUrl != null && !snap.videoStreamUrl.isEmpty()) {
            currentVideoUrl = snap.videoStreamUrl;
        }

        fpvWidget.setUsingMediaCodec(true);
        fpvWidget.setUrl(currentVideoUrl);
        fpvWidget.setPlayerType(PlayerType.ONLY_SKY);
        fpvWidget.setRtspTranstype(RtspTransport.AUTO);

        // 禁止自动重连：连接失败后不再自动重试
        fpvWidget.setReConnectInterceptor(() -> false);

        // 监听连接状态，更新 LIVE 指示器
        fpvWidget.setOnPlayerStateListener(new OnPlayerStateListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> setVideoConnected(true));
            }

            @Override
            public void onDisconnect() {
                runOnUiThread(() -> setVideoConnected(false));
            }

            @Override
            public void onReadFrame(com.skydroid.fpvplayer.ffmpeg.FrameInfo frameInfo) {
            }
        });

        fpvWidget.start();
    }

    private void setReceiverLinkConnected(boolean connected) {
        if (connected) {
            receiverLinkAlive = true;
            TcuLinkHub.setTrafficAlive(true);
        } else {
            receiverLinkAlive = false;
            TcuLinkHub.setTrafficAlive(false);
        }
        if (headerBar != null) {
            headerBar.setConnected(connected);
        }
    }

    /**
     * UDP 无连接握手：除 onConnectSuccess 外，收到任意回包或心跳 RTT 也视为已连接。
     */
    private void noteReceiverLinkAlive() {
        TcuLinkHub.setTrafficAlive(true);
        if (receiverLinkAlive) {
            return;
        }
        receiverLinkAlive = true;
        runOnUiThread(() -> setReceiverLinkConnected(true));
    }

    private void setVideoConnected(boolean connected) {
        if (bottomBar != null) bottomBar.setLiveStatus(connected);
        if (videoPlaceholder != null)
            videoPlaceholder.setVisibility(connected ? View.GONE : View.VISIBLE);
        if (noLiveVideoOverlay != null)
            noLiveVideoOverlay.setVisibility(connected ? View.GONE : View.VISIBLE);
    }

    private void reconnectVideo() {
        if (fpvWidget == null) return;
        fpvWidget.stop();
        fpvWidget.setUrl(currentVideoUrl);
        fpvWidget.start();
        Toast.makeText(this, "正在重新连接...", Toast.LENGTH_SHORT).show();
    }
    
    
    /**
     * 更新视频地址
     */
    private void updateVideoUrl(String url) {
        if (fpvWidget != null) {
            try {
                // 停止当前播放
                fpvWidget.stop();
                
                // 记录并设置新地址
                currentVideoUrl = url;
                fpvWidget.setUrl(url);
                ControllerLocalSettings.saveVideoStreamUrl(MainActivity.this, url);

                // 重新开始播放
                fpvWidget.start();
                
                Toast.makeText(this, "视频地址已更新", Toast.LENGTH_SHORT).show();
                Log.d("MainActivity", "视频地址更新为: " + url);
            } catch (Exception e) {
                Log.e("MainActivity", "更新视频地址失败: " + e.getMessage(), e);
                Toast.makeText(this, "更新失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setSensorStatusesOffline() {
        RtkState.clear();
        ImuStatusState.clear();
        ImuRealtimeState.clear();
        BucketTipHeightState.clear();
        lastUdpImuParsedSnapshot = null;
        if (headerBar != null) {
            headerBar.setRtkOnline(false);
            headerBar.setImuStatus(ImuStatusState.getOnlineCount(), ImuStatusState.TOTAL_COUNT, false);
        }
    }

    private void updateSensorStatuses(IMUDataParser.ParsedData parsed) {
        if (parsed == null) {
            return;
        }
        lastUdpImuParsedSnapshot = parsed;
        ImuStatusState.setOnlineCount(countOnlineImus(parsed));
        if (headerBar == null) {
            return;
        }
        boolean imuHealthyGreen = imuHeaderShowsHealthyGreen(parsed);
        headerBar.setImuStatus(ImuStatusState.getOnlineCount(), ImuStatusState.TOTAL_COUNT, imuHealthyGreen);
        headerBar.setRtkOnline(isValidRtk(parsed.rtkLat, parsed.rtkLon));
    }

    /**
     * 顶栏 IMU 绿色：需角度通道满；且若 TCU {@code 0x50}/{@code 0x51} 位图 IMU(bit6)=0 则绝不绿；
     * 位图未知时，若五路角全为 0 视为「无 IMU 占位」不绿（与现场约定一致）。
     */
    private boolean imuHeaderShowsHealthyGreen(IMUDataParser.ParsedData p) {
        int oc = countOnlineImus(p);
        if (oc != ImuStatusState.TOTAL_COUNT) {
            return false;
        }
        if (ImuStatusState.tcuDeniesImuHealthyGreen()) {
            return false;
        }
        if (ImuStatusState.tcuAssertsImuHealthyGreen()) {
            return true;
        }
        return !isAllImuAnglesEffectivelyZero(p);
    }

    private static boolean isAllImuAnglesEffectivelyZero(IMUDataParser.ParsedData p) {
        final float eps = 1e-4f;
        return Math.abs(p.boomAngle) < eps
                && Math.abs(p.stickAngle) < eps
                && Math.abs(p.bucketAngle) < eps
                && Math.abs(p.cabinPitchAngle) < eps
                && Math.abs(p.cabinRollAngle) < eps;
    }

    private void refreshHeaderImuFromTcuLinkState() {
        if (headerBar == null) {
            return;
        }
        IMUDataParser.ParsedData p = lastUdpImuParsedSnapshot;
        boolean imuHealthyGreen = p != null && imuHeaderShowsHealthyGreen(p);
        headerBar.setImuStatus(ImuStatusState.getOnlineCount(), ImuStatusState.TOTAL_COUNT, imuHealthyGreen);
        if (p != null) {
            headerBar.setRtkOnline(isValidRtk(p.rtkLat, p.rtkLon));
        }
    }

    private int countOnlineImus(IMUDataParser.ParsedData parsed) {
        int online = 0;
        if (isFinite(parsed.boomAngle)) online++;
        if (isFinite(parsed.stickAngle)) online++;
        if (isFinite(parsed.bucketAngle)) online++;
        if (isFinite(parsed.cabinPitchAngle) && isFinite(parsed.cabinRollAngle)) online++;
        return online;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean isValidRtk(double lat, double lon) {
        return RtkState.isValidCoordinate(lat, lon);
    }


    private void startDataUpdates() {
        // 主数据更新Handler（1秒更新一次）
        handler = new Handler(Looper.getMainLooper());
        
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateAllData();
                handler.postDelayed(this, 1000); // 每秒更新一次
            }
        };
        
        handler.post(updateRunnable);
        
        // 摇杆值更新Handler（50ms更新一次）
        joystickHandler = new Handler(Looper.getMainLooper());
        
        joystickUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateJoystickValues(); // 只更新摇杆值
                joystickHandler.postDelayed(this, 50); // 每50ms更新一次
            }
        };
        
        joystickHandler.post(joystickUpdateRunnable);
    }
    
    private void updateAllData() {
        // 更新连接信息
        updateConnectionInfo();
        
        // 更新机械臂角度（无论是否有接收机数据，都走统一的解算与显示）
        updateAngles();
        
        // 注意：摇杆值更新已独立到100ms循环中，不在这里更新
        
        // 更新定位信息（带小幅随机波动）
        updatePositioning();
        
        // 更新挖掘深度（带小幅随机波动）
//        updateDigDepth();
    }
    
    private void updateConnectionInfo() {
        if (!useRealData) {
            notifyLinkLatencyMs(-1);
        }
    }

    /** UDP 心跳测得的链路 RTT（毫秒），同步到底栏占位接口与顶栏显示。 */
    private void notifyLinkLatencyMs(int ms) {
        if (bottomBar != null) bottomBar.setDelay(ms);
        if (headerBar != null) headerBar.setLinkLatencyMs(ms);
    }
    
    /**
     * 更新信号强度显示
     */
    private void updateSignalDisplay() {
        if (bottomBar != null) bottomBar.setSignal(currentSignalStrength);
    }

    private void updateAngles() {
        float rawBoom;
        float rawStick;
        float rawBucket;
        float rawCabinPitch;
        float rawCabinRoll;

        if (useRealData) {
            // 使用真实UDP数据（原始IMU角度）
            rawBoom = realBoomAngle;
            rawStick = realStickAngle;
            rawBucket = realBucketAngle;
            rawCabinPitch = realCabinPitchAngle;
            rawCabinRoll = realCabinRollAngle;
        } else {
            // 无 UDP 业务数据：保持 0（原错误写法在参数里 rawX=0 会覆盖上面赋值，导致真实数据也从未参与解算）
            rawBoom = 0f;
            rawStick = 0f;
            rawBucket = 0f;
            rawCabinPitch = 0f;
            rawCabinRoll = 0f;
        }

        // 更新挖机姿态（使用原始 IMU 角度）
        if (postureCardView != null) {
            System.out.println("MainActivity: updateAngles: rawBoom=" + rawBoom + ", rawStick=" + rawStick + ", rawBucket=" + rawBucket);
            postureCardView.setAngles(
                    rawCabinPitch,
                    rawCabinRoll,
                    rawBoom,
                    rawStick,
                    rawBucket
            );
        }
        // 推送到 BottomBarView 组件
        if (bottomBar != null) {
            bottomBar.setAngles(rawBoom, rawStick, rawBucket,
                    rawCabinPitch, rawCabinRoll);
        }
        if (useRealData) {
            levelTaskGuidance.setImuAngles(rawBoom, rawStick, rawBucket);
            ImuRealtimeState.update(rawBoom, rawStick, rawBucket, rawCabinPitch, rawCabinRoll);
        }
        levelTaskGuidance.onImuUpdate(useRealData);
    }
    
    private void updatePositioning() {
        // RTK
        double lat;
        double lon;

        if (useRealData) {
            lat = realRtkLat;
            lon = realRtkLon;
        } else {
            // RTK默认位置（与 initMap / index.html 默认中心一致）
            lat = SIM_RTK_DEFAULT_LAT;
            lon = SIM_RTK_DEFAULT_LON;
        }

        if (mapView != null) mapView.setFixedLocation(lat, lon, 0.0);
        if (bottomBar != null) bottomBar.setRtkLatLon(lat, lon);
    }

//    private void updateDigDepth() {
//        double depth = 3.0 + random.nextDouble() * 0.5;
//        if (bottomBar != null) bottomBar.setDepth(depth);
//    }
//
    /**
     * 单例 callback：updateJoystickValues 每 50ms 调用一次（20Hz）。如果每次都 new 一个匿名内部类
     * + 一个 runOnUiThread lambda，一小时就是 7w+ 个短命对象，明显加剧 ART young-gen 的 GC 频率，
     * 表现为 RTSP 直播每隔几秒一次的 micro-stutter。这里把 callback 与 UI 投递的 Runnable 都提成
     * 字段单例，状态保存在 ch1~ch5Value，runOnUiThread 期间读字段即可。
     */
    private final Runnable joystickUiUpdater = new Runnable() {
        @Override
        public void run() {
            if (bottomBar != null) {
                int leftX = ch4Value;
                int leftY = ch3Value;
                int rightX = ch1Value;
                // ch2 在云卓内就是反向的，所以基础显示先取反；模式 reverse 再叠加处理。
                int rightY = -ch2Value;

                ControllerLocalSettings.Snapshot snap = currentJoystickUiMappingSnapshot;
                if (snap != null) {
                    if (snap.joystickLeftCdReverse) leftX = -leftX;
                    if (snap.joystickLeftAbReverse) leftY = -leftY;
                    if (snap.joystickRightGhReverse) rightX = -rightX;
                    if (snap.joystickRightEfReverse) rightY = -rightY;
                }

                bottomBar.setJoystickLeft(leftX, leftY);
                bottomBar.setJoystickRight(rightX, rightY);
            }
            updateJoystickRawDebugText();
            updateMotionModeFromChannel(ch5Value);
        }
    };

    private void updateJoystickRawDebugText() {
        if (joystickRawDebugText == null) {
            return;
        }
        joystickRawDebugText.setText(String.format(Locale.US,
                "CH1:%5d  CH2:%5d\nCH3:%5d  CH4:%5d",
                ch1Value, ch2Value, ch3Value, ch4Value));
    }

    private final CompletionCallbackWith<int[]> joystickValueCallback = new CompletionCallbackWith<int[]>() {
        @Override
        public void onSuccess(int[] value) {
            // 区间【-450，450】
            if (value != null && value.length >= 5) {
                ch1Value = value[0] - 1500; // 右摇杆左右
                ch2Value = value[1] - 1500; // 右摇杆上下
                ch3Value = value[2] - 1500; // 左摇杆上下
                ch4Value = value[3] - 1500; // 左摇杆左右
                ch5Value = value[4] - 1500; // 模式切换
                runOnUiThread(joystickUiUpdater);
            }
        }

        @Override
        public void onFailure(SkyException e) {
            Log.e("MainActivity", "摇杆值获取失败: " + (e != null ? e.getMessage() : "未知错误"));
        }
    };

    /**
     * 更新摇杆值
     */
    private void updateJoystickValues() {
        if (!remoteControllerConnected) {
            return;
        }
        KeyManager.INSTANCE.get(RemoteControllerKey.INSTANCE.getKeyChannels(),
                joystickValueCallback);
    }

    private void updateMotionModeFromChannel(int channelValue) {
        if (motionModeSegment == null) {
            return;
        }

        int selectedIndex;
        if (channelValue <= -225) {
            selectedIndex = MotionModeSegmentView.INDEX_STOP;
        } else if (channelValue >= 225) {
            selectedIndex = MotionModeSegmentView.INDEX_BUCKET;
        } else {
            selectedIndex = MotionModeSegmentView.INDEX_CHASSIS;
        }
        motionModeSegment.setSelectedIndex(selectedIndex);
    }

    private void onMotionModeChanged(int selectedIndex) {
        desiredMotionModeChannelIndex = selectedIndex;
        applyMotionModeChannelMapping(selectedIndex);
    }

    private void applyMotionModeChannelMapping(int selectedIndex) {
        final int generation = motionModeChannelApplyGeneration.incrementAndGet();
        MotionModeChannelMappingManager.applyForMode(this, selectedIndex, e -> {
            if (generation != motionModeChannelApplyGeneration.get()) {
                applyMotionModeChannelMapping(desiredMotionModeChannelIndex);
                return;
            }
            if (e == null) {
                refreshJoystickUiMappingSnapshotForMode(selectedIndex);
                Log.i("MainActivity", "运动模式通道配置已切换: " + selectedIndex);
            } else {
                Log.e("MainActivity", "运动模式通道配置切换失败: " + e.getMessage());
            }
        });
    }

    private void refreshJoystickUiMappingSnapshotForMode(int selectedIndex) {
        currentJoystickUiMappingSnapshot =
                MotionModeChannelMappingManager.resolveSnapshotForMode(this, selectedIndex);
    }
    
    /**
     * 初始化SDK
     */
    private void initSDK() {
        // 记得改回来
        createUDPPipeline();
        // TODO 初始化SDK,初始化一次即可
        RCSDKManager.INSTANCE.initSDK(this, new SDKManagerCallBack() {
            @Override
            public void onRcConnected() {
                remoteControllerConnected = true;
                Log.d("MainActivity", "遥控器连接成功");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "遥控器连接成功", Toast.LENGTH_SHORT).show();
                });
                // 遥控器连接成功后，创建UDP管道
                // createUDPPipeline();
                // 调试：拉取并打印 KeyChannelSettings / g20 通道表（mapping、行程、反向等），便于对照实机整理 mapping 整型
                RcChannelSettingsHelper.logChannelSettingsAfterDelay(MainActivity.this, 1500);
                // 首次连接控制器时按默认布局（ch1=铲斗, ch2=大臂, ch3=小臂, ch4=回旋）快照 mapping
                // 基准码；之后摇杆映射设置改动即可通过 JoystickChannelMappingApplier 下发。
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> JoystickChannelMappingApplier.captureBaselineIfNeeded(MainActivity.this),
                        1800);
            }
            
            @Override
            public void onRcConnectFail(SkyException e) {
                remoteControllerConnected = false;
                Log.e("MainActivity", "遥控器连接失败: " + (e != null ? e.getMessage() : "未知错误"));
                runOnUiThread(() -> {
                    setReceiverLinkConnected(false);
                    Toast.makeText(MainActivity.this, "遥控器连接失败", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onRcDisconnect() {
                remoteControllerConnected = false;
                Log.e("MainActivity", "遥控器断开连接");
                runOnUiThread(() -> {
                    setReceiverLinkConnected(false);
                    Toast.makeText(MainActivity.this, "遥控器断开连接", Toast.LENGTH_SHORT).show();
                });
            }
        });
        
        // 设置在主线程回调
        RCSDKManager.INSTANCE.setMainThreadCallBack(true);
        
        // 连接到遥控器
        RCSDKManager.INSTANCE.connectToRC();
        
        // 注册信号强度监听器
        keySignalQualityListener = new KeyListener<Integer>() {
            @Override
            public void onValueChange(Integer oldValue, Integer newValue) {
                // newValue 是信号强度百分比 (0-100)
                currentSignalStrength = newValue != null ? newValue : 0;
                // 在主线程更新UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateSignalDisplay();
                    }
                });
            }
        };
        KeyManager.INSTANCE.listen(AirLinkKey.INSTANCE.getKeySignalQuality(), keySignalQualityListener);
    }
    
    private void bindTcuLinkHub() {
        TcuLinkHub.setSender(new TcuLinkHub.Sender() {
            @Override
            public boolean isConnected() {
                return udpPipeline != null && udpPipeline.isConnected();
            }

            @Override
            public void write(byte[] data) {
                if (udpPipeline != null) {
                    udpPipeline.writeData(data);
                }
            }
        });
    }

    /**
     * 创建UDP管道
     */
    private void createUDPPipeline() {
        // 避免 SDK 多次 onRcConnected 时叠多套管道与重复失败回调
        if (udpPipeline != null) {
            PipelineManager.INSTANCE.disconnectPipeline(udpPipeline);
            udpPipeline = null;
        }
        // 创建UDP管道：本地端口14551，发送到127.0.0.1:14552
        udpPipeline = PipelineManager.INSTANCE.createUDPPipeline(14551, "192.168.192.157", 14552);

        bindTcuLinkHub();
        // 设置通信监听器
        udpPipeline.setOnCommListener(new CommListener() {
            @Override
            public void onConnectSuccess() {
                Log.d("UDP", "UDP管道连接成功");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 连接成功但不立即切换，等待收到数据后再切换
                        // useRealData 保持 false，直到收到第一个数据包
                        lastDataReceiveTime = System.currentTimeMillis();
                        noteReceiverLinkAlive();
                        Toast.makeText(MainActivity.this, "UDP连接成功，等待数据...", Toast.LENGTH_SHORT).show();
                        startHeartbeat(); // 开始定时发送心跳帧测量 RTT
                    }
                });
            }

            @Override
            public void onConnectFail(SkyException e) {
                Log.e("UDP", "UDP管道连接失败: " + (e != null ? e.getMessage() : "未知错误"));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        useRealData = false; // 连接失败，使用模拟数据
                        setReceiverLinkConnected(false);
                        setSensorStatusesOffline();
                        notifyLinkLatencyMs(-1);
                        Toast.makeText(MainActivity.this, "UDP连接失败，使用模拟数据", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onDisconnect() {
                Log.d("UDP", "UDP管道断开连接");
                stopHeartbeat();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        useRealData = false; // 切换回模拟数据
                        setReceiverLinkConnected(false);
                        TcuLinkHub.setSender(null);
    TcuInitHandshake.reset();
                        TcuInitHandshake.reset();
                        setSensorStatusesOffline();
                        notifyLinkLatencyMs(-1);
                        Toast.makeText(MainActivity.this, "UDP断开连接", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onReadData(byte[] data) {
                if (data != null && data.length > 0) {
                    noteReceiverLinkAlive();
                    Log.d("UDP", "收到数据，长度: " + data.length);

                    // §6.1：0x50 初始化 → 自动回 0xD0，TCU 进入主程序后才发 0xFA 实时流
                    if (TcuInitHandshake.tryHandle(data)) {
                        runOnUiThread(() -> {
                            refreshHeaderImuFromTcuLinkState();
                            if (TcuInitHandshake.isMainProgramEntered()) {
                                Toast.makeText(MainActivity.this,
                                        "TCU 初始化完成，等待实时数据…",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }

                    // 0x51 链路心跳 LinkBitmap——与 33 字节实时流分离
                    if (TcuBusinessFrameParser.tryConsumeAndUpdateImuLink(data)) {
                        runOnUiThread(MainActivity.this::refreshHeaderImuFromTcuLinkState);
                        return;
                    }

                    if (TcuBusinessCodec.isBusinessFrame(data) && TcuLinkHub.dispatch(data)) {
                        return;
                    }

                    // 优先判断是否为心跳回包（header = 0xFB 0xFB）
                    if (data.length == 33
                            && data[0] == HEARTBEAT_HEADER_1
                            && data[1] == HEARTBEAT_HEADER_2
                            && data[2] == HEARTBEAT_TYPE) {
                        // 从帧中还原发送时间戳（big-endian，frame[3..10]）
                        long sendTs = 0;
                        for (int i = 0; i < 8; i++) {
                            sendTs = (sendTs << 8) | (data[3 + i] & 0xFF);
                        }
                        if (sendTs > 0) {
                            int rtt = (int) (System.currentTimeMillis() - sendTs);
                            Log.d("RTT", "收到心跳回包，RTT=" + rtt + "ms");
                            runOnUiThread(() -> {
                                noteReceiverLinkAlive();
                                notifyLinkLatencyMs(rtt);
                            });
                        }
                        return; // 心跳回包不进入业务数据解析
                    }

                    if (data.length == 33) {
                        // 更新最后接收数据的时间
                        long now = System.currentTimeMillis();
                        lastDataReceiveTime = now;

                        // 解析数据
                        IMUDataParser.parseData(data, new IMUDataParser.ParseResultCallbackV2() {
                            @Override
                            public void onParseSuccess(IMUDataParser.ParsedData parsed) {
                                Log.i("UDP", String.format(Locale.US,
                                    "IMU boom=%.2f stick=%.2f bucket=%.2f cabinP=%.2f cabinR=%.2f enc=%.2f lat=%.9f lon=%.9f",
                                    parsed.boomAngle, parsed.stickAngle, parsed.bucketAngle,
                                    parsed.cabinPitchAngle, parsed.cabinRollAngle, parsed.encoderAngle,
                                    parsed.rtkLat, parsed.rtkLon));
                                // ????IMU?????????????
                                realBoomAngle = parsed.boomAngle;
                                realStickAngle = parsed.stickAngle;
                                realBucketAngle = parsed.bucketAngle;
                                realCabinPitchAngle = parsed.cabinPitchAngle;
                                realCabinRollAngle = parsed.cabinRollAngle;
                                ImuRealtimeState.update(
                                        parsed.boomAngle,
                                        parsed.stickAngle,
                                        parsed.bucketAngle,
                                        parsed.cabinPitchAngle,
                                        parsed.cabinRollAngle);
                                // ??RTK?????????
                                realRtkLat = parsed.rtkLat;
                                realRtkLon = parsed.rtkLon;
                                RtkState.update(parsed.rtkLat, parsed.rtkLon);
                                runOnUiThread(() -> {
                                    // 解析成功后才认为 IMU/RTK 有数据
                                    boolean justWentReal = !useRealData;
                                    if (justWentReal) {
                                        useRealData = true;
                                        Log.d("UDP", "收到UDP数据，切换到真实数据模式");
                                    }
                                    updateSensorStatuses(parsed);
                                    if (udpTimeoutHandler != null && udpTimeoutRunnable != null) {
                                        udpTimeoutHandler.removeCallbacks(udpTimeoutRunnable);
                                    }
                                    startUDPTimeoutCheck();

                                    levelTaskGuidance.setImuAngles(
                                            parsed.boomAngle,
                                            parsed.stickAngle,
                                            parsed.bucketAngle);
                                    levelTaskGuidance.onImuUpdate(useRealData);
                                });
                            }
                            @Override
                            public void onParseError(String error) {
                                System.out.println("MainActivity: onParseError: error=" + error);
                                Log.e("UDP", "数据解析失败: " + error);
                                runOnUiThread(() -> {
                                    useRealData = false;
                                    setSensorStatusesOffline();
                                    notifyLinkLatencyMs(-1);
                                });
                            }
                        });
                    } else {
                        Log.w("UDP", "非业务帧且长度不是 33 字节: " + data.length);
                    }
                }
            }
        });

        // 连接UDP管道
        PipelineManager.INSTANCE.connectPipeline(udpPipeline);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (udpPipeline != null && udpPipeline.isConnected()) {
                noteReceiverLinkAlive();
                startHeartbeat();
            }
        }, 400);
    }
    
    /**
     * 构建心跳帧（33字节）。
     * 格式：0xFB 0xFB | type(1) | timestamp(8, big-endian) | padding(19) | CRC16(2) | 0xFF
     * 机载端识别到 header=0xFB 0xFB 且 type=0x01 时，原样将整帧回传给 App。
     */
    private byte[] buildHeartbeatFrame(long timestamp) {
        byte[] frame = new byte[33];
        frame[0] = HEARTBEAT_HEADER_1;
        frame[1] = HEARTBEAT_HEADER_2;
        // data 区（28字节，偏移2~29）
        frame[2] = HEARTBEAT_TYPE;
        // 8字节时间戳（big-endian），放在 data[1..8]（即 frame[3..10]）
        for (int i = 0; i < 8; i++) {
            frame[3 + i] = (byte) ((timestamp >> (56 - 8 * i)) & 0xFF);
        }
        // 剩余 19 字节填充 0（frame[11..29] 已为 0）
        // CRC 计算范围：data 区 28 字节（frame[2..29]）
        byte[] dataForCrc = new byte[28];
        System.arraycopy(frame, 2, dataForCrc, 0, 28);
        int crc = CRC16Modbus.calculateCRC16Modbus(dataForCrc);
        byte[] crcBytes = CRC16Modbus.crcToBytes(crc);
        frame[30] = crcBytes[0];
        frame[31] = crcBytes[1];
        frame[32] = (byte) 0xFF;
        return frame;
    }

    /**
     * 启动心跳定时器，每秒向机载端发送一次心跳帧。
     * 机载端收到后原样回传，App 在 onReadData 中计算 RTT。
     */
    private void startHeartbeat() {
        if (heartbeatHandler == null) {
            heartbeatHandler = new Handler(Looper.getMainLooper());
        }
        stopHeartbeat();
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (udpPipeline != null && udpPipeline.isConnected()) {
                    long sendTime = System.currentTimeMillis();
                    udpPipeline.writeData(buildHeartbeatFrame(sendTime));
                    Log.d("RTT", "心跳已发送，时间戳: " + sendTime);
                }
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        };
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
    }

    /** 停止心跳定时器并清零发送时间戳 */
    private void stopHeartbeat() {
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }
    }

    /**
     * 启动UDP数据接收超时检查
     * 如果长时间没收到数据，自动切换回模拟数据
     */
    private void startUDPTimeoutCheck() {
        if (udpTimeoutHandler == null) {
            udpTimeoutHandler = new Handler(Looper.getMainLooper());
        }
        
        if (udpTimeoutRunnable != null) {
            udpTimeoutHandler.removeCallbacks(udpTimeoutRunnable);
        }
        
        udpTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                if (useRealData && (currentTime - lastDataReceiveTime) > UDP_TIMEOUT_MS) {
                    // 超过5秒没收到数据，切换回模拟数据
                    useRealData = false;
                    stopHeartbeat(); // 停止心跳，不再测量延迟
                    Log.w("UDP", "UDP数据接收超时，切换回模拟数据");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setSensorStatusesOffline();
                            notifyLinkLatencyMs(-1);
                            Toast.makeText(MainActivity.this, "UDP数据超时，使用模拟数据", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else if (useRealData) {
                    // 继续检查
                    udpTimeoutHandler.postDelayed(this, 1000); // 每秒检查一次
                }
            }
        };
        
        udpTimeoutHandler.postDelayed(udpTimeoutRunnable, UDP_TIMEOUT_MS);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            ControllerLocalSettings.Snapshot snap = ControllerLocalSettings.load(this);
            String resolved = (snap.videoStreamUrl != null && !snap.videoStreamUrl.isEmpty())
                    ? snap.videoStreamUrl
                    : DEFAULT_VIDEO_URL;
            updateVideoUrl(resolved);
            applyStoredArmLengthScalesToWebView();
            // Re-load IMU config from SharedPreferences whenever settings are saved
            initImuAngleConfig();
            if (motionModeSegment != null
                    && motionModeSegment.getSelectedIndex() == MotionModeSegmentView.INDEX_BUCKET) {
                refreshJoystickUiMappingSnapshotForMode(MotionModeSegmentView.INDEX_BUCKET);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webAppPreloader != null) {
            webAppPreloader.cleanup();
            webAppPreloader = null;
        }
        ExcavatorWebAppBridge.setMessageListener(null);
        
        // 停止视频播放
        if (fpvWidget != null) {
            fpvWidget.stop();
        }
        
        // 停止主数据更新
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
        
        // 停止摇杆值更新
        if (joystickHandler != null && joystickUpdateRunnable != null) {
            joystickHandler.removeCallbacks(joystickUpdateRunnable);
        }
        
        // 停止UDP超时检查
        if (udpTimeoutHandler != null && udpTimeoutRunnable != null) {
            udpTimeoutHandler.removeCallbacks(udpTimeoutRunnable);
        }

        // 停止心跳
        stopHeartbeat();
        
        // 断开UDP管道
        if (udpPipeline != null) {
            PipelineManager.INSTANCE.disconnectPipeline(udpPipeline);
            udpPipeline = null;
        }
        TcuLinkHub.setSender(null);
        TcuInitHandshake.reset();
        
        // 断开遥控器连接
        RCSDKManager.INSTANCE.disconnectRC();
        
        // 取消信号强度监听
        if (keySignalQualityListener != null) {
            KeyManager.INSTANCE.cancelListen(keySignalQualityListener);
            keySignalQualityListener = null;
        }
    }
}
