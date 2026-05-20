package com.capstone.excavator;

import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 找平作业设置页。
 *
 * Step1：选择参考点（左斗尖 / 中斗尖 / 右斗尖）
 * Step2：高度定点 / 坐标定点 模式切换 + 输入目标高度 & 填挖量（NumpadView）
 * 右侧：挖机预览图 + 下一步按钮
 */
public class LevelSettingActivity extends ScaledAppCompatActivity {

    // ── 参考点选择 ───────────────────────────────────────────
    private LinearLayout cardRefLeft, cardRefMiddle, cardRefRight;
    private TextView tvRefLeft, tvRefMiddle, tvRefRight;
    private int selectedRef = 1; // 0=左 1=中 2=右，默认中斗尖

    // ── 模式切换 ─────────────────────────────────────────────
    private TextView btnModeHeight, btnModeCoord;
    private boolean isHeightMode = true;
    private View panelHeightMode, panelCoordMode;

    // ── 数值输入 ─────────────────────────────────────────────
    private TextView tvTargetHeight, tvFillCut;
    private TextView tvCoordX, tvCoordY, tvCoordZ;
    private TextView tvCurrentLatLon;
    private NumpadView numpad;

    // ── 距离标注 ─────────────────────────────────────────────
    private TextView tvDepthLabel;
    private TextView tvCurrentRef;

    // ── 计算/管理状态 ─────────────────────────────────────────
    // 高度定点（§6.2 0x90）：
    // - tvCurrentRef「测量值」：斗尖 RTK 测点高度 M（只读）
    // - tvTargetHeight「填挖量」F：用户输入
    // - tvFillCut「设计高程」：自动 = F + M（只读联动）
    // - 下发 0x11：TargetHeight = M + F = 设计高程
    // 坐标定点：经纬度/高度来自同一次 0x90，目标字段可再编辑

    // ── 其他控件 ─────────────────────────────────────────────
    private View btnLevelBack, btnLevelNext;
    private View btnLevelHelp;
    private HelpTooltip helpTooltip;
    private boolean workflowBusy;
    private final LevelTcuWorkflow workflow = LevelTcuWorkflow.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        setContentView(R.layout.activity_level_setting);

        bindViews();
        initNumpad();
        setupRefCards();
        setupModeToggle();
        setupInputs();
        restoreFromState();
        refreshDerivedViews();
        setupActions();
        // 先完成布局再自动发起 TCU 流程，避免首帧返回键被 busy 锁住
        if (btnLevelBack != null) {
            btnLevelBack.post(this::enterLevelFeatureIfNeeded);
        } else {
            enterLevelFeatureIfNeeded();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSurveyLatLonDisplay();
        refreshSurveyMeasurementDisplay();
        syncCoordTargetsFromSurveyIfEmpty();
        refreshDerivedViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LevelTcuWorkflow.setSurveyStoredListener(this::onSurveyStoredFromTcu);
        refreshSurveyLatLonDisplay();
        refreshSurveyMeasurementDisplay();
    }

    @Override
    protected void onStop() {
        LevelTcuWorkflow.setSurveyStoredListener(null);
        super.onStop();
        if (helpTooltip != null) helpTooltip.dismiss();
        if (numpad != null && numpad.isShowing()) numpad.dismiss();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setFullScreenMode();
    }

    /**
     * §6.2：找平参考点经纬度来自 TCU 测点应答 0x90，不是 0xFA 实时 RTK 流。
     */
    private void refreshSurveyLatLonDisplay() {
        applySurveyLatLon(
                LevelTaskState.getSurveyLat(),
                LevelTaskState.getSurveyLon(),
                LevelTaskState.hasSurveyHeight());
    }

    private void applySurveyLatLon(double lat, double lon, boolean hasSurvey) {
        if (tvCurrentLatLon == null) {
            return;
        }
        if (hasSurvey && RtkState.isValidCoordinate(lat, lon)) {
            tvCurrentLatLon.setText(String.format(Locale.US, "%.9f, %.9f", lat, lon));
            return;
        }
        if (workflowBusy && !hasSurvey) {
            tvCurrentLatLon.setText("测点中…");
        } else {
            tvCurrentLatLon.setText("待测点");
        }
    }

    /** 0x90 测点高度 → 高度定点「测量值」；经纬度 → 坐标定点各字段。 */
    private void applySurveyResultToUi(double heightM, double lat, double lon) {
        refreshSurveyMeasurementDisplay();
        applySurveyToCoordTargets(heightM, lat, lon);
        refreshDerivedViews();
    }

    /** §6.2：测量值 = 当前斗尖测点高度（0x90 Height），不被填挖量/设计高程覆盖。 */
    private void refreshSurveyMeasurementDisplay() {
        if (tvCurrentRef == null) {
            return;
        }
        if (LevelTaskState.hasSurveyHeight()) {
            tvCurrentRef.setText(formatMetersValue(LevelTaskState.getSurveyHeightM()));
            tvCurrentRef.setTextColor(getColor(R.color.level_selected));
            return;
        }
        tvCurrentRef.setTextColor(getColor(R.color.level_unselected));
        if (workflowBusy) {
            tvCurrentRef.setText("测点中…");
        } else {
            tvCurrentRef.setText("--");
        }
    }

    /** 0x90 测点结果 → 坐标定点：目标经度 / 目标纬度 / 目标高度(m)。 */
    private void applySurveyToCoordTargets(double heightM, double lat, double lon) {
        applySurveyLatLon(lat, lon, true);
        if (RtkState.isValidCoordinate(lat, lon)) {
            if (tvCoordX != null) {
                tvCoordX.setText(formatCoordValue(lon));
            }
            if (tvCoordY != null) {
                tvCoordY.setText(formatCoordValue(lat));
            }
        }
        if (!Double.isNaN(heightM) && tvCoordZ != null) {
            tvCoordZ.setText(formatMetersValue(heightM));
        }
        cacheState();
    }

    private void syncCoordTargetsFromSurveyIfEmpty() {
        if (!LevelTaskState.hasSurveyHeight()) {
            return;
        }
        double lat = LevelTaskState.getSurveyLat();
        double lon = LevelTaskState.getSurveyLon();
        double heightM = LevelTaskState.getSurveyHeightM();
        if (RtkState.isValidCoordinate(lat, lon)) {
            if (isUnsetCoordField(tvCoordX, LevelTaskState.getTargetLon())) {
                if (tvCoordX != null) {
                    tvCoordX.setText(formatCoordValue(lon));
                }
            }
            if (isUnsetCoordField(tvCoordY, LevelTaskState.getTargetLat())) {
                if (tvCoordY != null) {
                    tvCoordY.setText(formatCoordValue(lat));
                }
            }
        }
        if (isUnsetCoordField(tvCoordZ, LevelTaskState.getTargetZ())) {
            if (tvCoordZ != null) {
                tvCoordZ.setText(formatMetersValue(heightM));
            }
        }
        cacheState();
    }

    private static boolean isUnsetCoordField(@Nullable TextView field, String stored) {
        if (stored != null && !stored.isEmpty() && !"0".equals(stored.trim()) && !"--".equals(stored.trim())) {
            return false;
        }
        if (field == null) {
            return true;
        }
        CharSequence text = field.getText();
        if (text == null) {
            return true;
        }
        String s = text.toString().trim();
        return s.isEmpty() || "0".equals(s) || "--".equals(s) || "待测点".equals(s) || "测点中…".equals(s);
    }

    private void onSurveyStoredFromTcu(double heightM, double lat, double lon) {
        workflowBusy = false;
        if (btnLevelNext != null) {
            btnLevelNext.setEnabled(true);
        }
        applySurveyResultToUi(heightM, lat, lon);
    }

    @Override
    public void onBackPressed() {
        exitLevelAndFinish();
    }

    private void setFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void bindViews() {
        cardRefLeft   = findViewById(R.id.cardRefLeft);
        cardRefMiddle = findViewById(R.id.cardRefMiddle);
        cardRefRight  = findViewById(R.id.cardRefRight);

        tvRefLeft   = findViewById(R.id.tvRefLeft);
        tvRefMiddle = findViewById(R.id.tvRefMiddle);
        tvRefRight  = findViewById(R.id.tvRefRight);

        btnModeHeight = findViewById(R.id.btnModeHeight);
        btnModeCoord  = findViewById(R.id.btnModeCoord);

        panelHeightMode = findViewById(R.id.panelHeightMode);
        panelCoordMode  = findViewById(R.id.panelCoordMode);

        tvTargetHeight = findViewById(R.id.tvTargetHeight);
        tvFillCut      = findViewById(R.id.tvFillCut);
        tvCurrentRef   = findViewById(R.id.tvCurrentRef);
        tvCoordX       = findViewById(R.id.tvCoordX);
        tvCoordY       = findViewById(R.id.tvCoordY);
        tvCoordZ       = findViewById(R.id.tvCoordZ);
        tvCurrentLatLon = findViewById(R.id.tvCurrentLatLon);
        tvDepthLabel   = findViewById(R.id.tvDepthLabel);

        btnLevelBack = findViewById(R.id.btnLevelBack);
        btnLevelNext = findViewById(R.id.btnLevelNext);
        btnLevelHelp = findViewById(R.id.btnLevelHelp);
    }

    private void initNumpad() {
        numpad = new NumpadView(this);
    }

    private void restoreFromState() {
        selectedRef = LevelTaskState.getReferencePoint();
        isHeightMode = LevelTaskState.isHeightMode();
        applyRefSelection();
        applyModeSelection();

        if (tvTargetHeight != null && !LevelTaskState.getTargetHeight().isEmpty()) {
            tvTargetHeight.setText(LevelTaskState.getTargetHeight());
        }
        if (tvFillCut != null && !LevelTaskState.getFillCut().isEmpty()) {
            tvFillCut.setText(LevelTaskState.getFillCut());
        }
        if (tvCoordX != null && !LevelTaskState.getTargetLon().isEmpty()) {
            tvCoordX.setText(LevelTaskState.getTargetLon());
        }
        if (tvCoordY != null && !LevelTaskState.getTargetLat().isEmpty()) {
            tvCoordY.setText(LevelTaskState.getTargetLat());
        }
        if (tvCoordZ != null && !LevelTaskState.getTargetZ().isEmpty()) {
            tvCoordZ.setText(LevelTaskState.getTargetZ());
        }
        refreshSurveyLatLonDisplay();
        refreshSurveyMeasurementDisplay();
        syncCoordTargetsFromSurveyIfEmpty();
        refreshDerivedViews();
    }

    // ── 参考点卡片 ────────────────────────────────────────────

    private void setupRefCards() {
        cardRefLeft.setOnClickListener(v -> selectRef(0));
        cardRefMiddle.setOnClickListener(v -> selectRef(1));
        cardRefRight.setOnClickListener(v -> selectRef(2));
        applyRefSelection();
    }

    private void selectRef(int index) {
        selectedRef = index;
        applyRefSelection();
        cacheState();
        refreshSurveyMeasurementDisplay();
        requestSurveyForSelectedRef();
    }

    private void requestSurveyForSelectedRef() {
        if (workflowBusy) {
            return;
        }
        setWorkflowBusy(true, "测点中…");
        workflow.requestSurvey(selectedRef, new LevelTcuWorkflow.SurveyCallback() {
            @Override
            public void onSurveyResult(double heightM, double lat, double lon) {
                onSurveyStoredFromTcu(heightM, lat, lon);
            }

            @Override
            public void onSuccess() {
                Toast.makeText(LevelSettingActivity.this, "参考点测点成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false, null);
                Toast.makeText(LevelSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyRefSelection() {
        cardRefLeft.setBackground(getDrawable(selectedRef == 0
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        tvRefLeft.setTextColor(getColor(selectedRef == 0 ? R.color.level_selected : R.color.level_unselected));

        cardRefMiddle.setBackground(getDrawable(selectedRef == 1
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        tvRefMiddle.setTextColor(getColor(selectedRef == 1 ? R.color.level_selected : R.color.level_unselected));

        cardRefRight.setBackground(getDrawable(selectedRef == 2
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        tvRefRight.setTextColor(getColor(selectedRef == 2 ? R.color.level_selected : R.color.level_unselected));
    }

    // ── 模式切换 ──────────────────────────────────────────────

    private void setupModeToggle() {
        btnModeHeight.setOnClickListener(v -> switchToHeightFixedPoint());
        btnModeCoord.setOnClickListener(v -> switchToRtkFixedPoint());
        applyModeSelection();
    }

    /** 高度定点：仅切换 UI，不重复测点。 */
    private void switchToHeightFixedPoint() {
        setMode(true);
    }

    /**
     * 坐标定点：切换面板后按当前斗尖再发一次 0x10 测点，用 0x90 刷新经纬度/高度。
     */
    private void switchToRtkFixedPoint() {
        setMode(false);
        requestSurveyForSelectedRef();
    }

    private void setMode(boolean heightMode) {
        isHeightMode = heightMode;
        applyModeSelection();
        cacheState();
        refreshSurveyMeasurementDisplay();
        refreshSurveyLatLonDisplay();
        refreshDerivedViews();
    }

    private void applyModeSelection() {
        if (isHeightMode) {
            btnModeHeight.setBackground(getDrawable(R.drawable.level_mode_selected_bg));
            btnModeHeight.setTextColor(getColor(R.color.level_selected));
            btnModeHeight.setTypeface(null, android.graphics.Typeface.BOLD);

            btnModeCoord.setBackground(null);
            btnModeCoord.setTextColor(getColor(R.color.level_unselected));
            btnModeCoord.setTypeface(null, android.graphics.Typeface.NORMAL);

            if (panelHeightMode != null) panelHeightMode.setVisibility(View.VISIBLE);
            if (panelCoordMode != null) panelCoordMode.setVisibility(View.GONE);
        } else {
            btnModeCoord.setBackground(getDrawable(R.drawable.level_mode_selected_bg));
            btnModeCoord.setTextColor(getColor(R.color.level_selected));
            btnModeCoord.setTypeface(null, android.graphics.Typeface.BOLD);

            btnModeHeight.setBackground(null);
            btnModeHeight.setTextColor(getColor(R.color.level_unselected));
            btnModeHeight.setTypeface(null, android.graphics.Typeface.NORMAL);

            if (panelHeightMode != null) panelHeightMode.setVisibility(View.GONE);
            if (panelCoordMode != null) panelCoordMode.setVisibility(View.VISIBLE);
        }
    }

    // ── Numpad 输入 ───────────────────────────────────────────

    private void setupInputs() {
        tvTargetHeight.setOnClickListener(v -> {
            if (numpad.isShowing()) { numpad.dismiss(); return; }
            numpad.setOnConfirmListener(value -> {
                tvTargetHeight.setText(value);
                refreshDerivedViews();
            });
            numpad.showForAtScreen(tvTargetHeight, tvTargetHeight,
                    NumpadPositionConfig.SCREEN_X, NumpadPositionConfig.SCREEN_Y);
        });

        tvCoordX.setOnClickListener(v -> {
            if (numpad.isShowing()) { numpad.dismiss(); return; }
            numpad.setOnConfirmListener(value -> tvCoordX.setText(value));
            numpad.showForAtScreen(tvCoordX, tvCoordX,
                    NumpadPositionConfig.SCREEN_X, NumpadPositionConfig.SCREEN_Y);
        });

        tvCoordY.setOnClickListener(v -> {
            if (numpad.isShowing()) { numpad.dismiss(); return; }
            numpad.setOnConfirmListener(value -> tvCoordY.setText(value));
            numpad.showForAtScreen(tvCoordY, tvCoordY,
                    NumpadPositionConfig.SCREEN_X, NumpadPositionConfig.SCREEN_Y);
        });

        tvCoordZ.setOnClickListener(v -> {
            if (numpad.isShowing()) { numpad.dismiss(); return; }
            numpad.setOnConfirmListener(value -> tvCoordZ.setText(value));
            numpad.showForAtScreen(tvCoordZ, tvCoordZ,
                    NumpadPositionConfig.SCREEN_X, NumpadPositionConfig.SCREEN_Y);
        });
    }

    /** 设计高程 = 填挖量(tvTargetHeight) + 测量值(tvCurrentRef / 0x90)。 */
    private void syncDesignElevationFromInputs() {
        if (tvFillCut == null) {
            return;
        }
        Double fillAmount = parseDoubleOrNull(tvTargetHeight == null ? null : tvTargetHeight.getText());
        Double measurementM = getSurveyMeasurementM();
        if (fillAmount == null || measurementM == null) {
            return;
        }
        tvFillCut.setText(formatMetersValue(fillAmount + measurementM));
    }

    @Nullable
    private Double getSurveyMeasurementM() {
        if (LevelTaskState.hasSurveyHeight()) {
            return LevelTaskState.getSurveyHeightM();
        }
        return parseDoubleOrNull(tvCurrentRef == null ? null : tvCurrentRef.getText());
    }

    private void refreshDerivedViews() {
        if (tvTargetHeight == null || tvFillCut == null || tvCurrentRef == null || tvDepthLabel == null) {
            return;
        }

        refreshSurveyMeasurementDisplay();
        syncDesignElevationFromInputs();

        Double designElev = parseDoubleOrNull(tvFillCut.getText());
        Double measurementM = getSurveyMeasurementM();

        if (designElev == null || measurementM == null) {
            tvDepthLabel.setText("-- m");
            cacheState();
            return;
        }

        String designText = formatMetersValue(designElev);
        tvDepthLabel.setText(designText + " m");
        tvDepthLabel.setTextColor(designElev < measurementM
                ? Color.parseColor("#FFEF4444")
                : Color.parseColor("#FF22C55E"));

        cacheState();
    }

    private void cacheState() {
        // 页面内随改随存，保证下一页/返回时状态一致
        LevelTaskState.update(
                selectedRef,
                isHeightMode,
                tvTargetHeight == null ? "" : tvTargetHeight.getText().toString(),
                tvFillCut == null ? "" : tvFillCut.getText().toString(),
                tvCoordX == null ? "" : tvCoordX.getText().toString(),
                tvCoordY == null ? "" : tvCoordY.getText().toString(),
                tvCoordZ == null ? "" : tvCoordZ.getText().toString()
        );
    }

    private static Double parseDoubleOrNull(CharSequence text) {
        if (text == null) return null;
        String s = text.toString().trim();
        if (s.isEmpty() || s.equals("--")) return null;
        // 兼容 “−” (U+2212) 负号
        s = s.replace('−', '-');
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatMetersValue(double v) {
        // 使用 2 位小数，且把普通 '-' 替换为更像 UI 的 “−”
        String s = String.format(Locale.US, "%.2f", v);
        return s.startsWith("-") ? "−" + s.substring(1) : s;
    }

    private static String formatCoordValue(double v) {
        return String.format(Locale.US, "%.9f", v);
    }

    // ── 按钮动作 ──────────────────────────────────────────────

    private void setupActions() {
        btnLevelBack.setOnClickListener(v -> exitLevelAndFinish());

        helpTooltip = new HelpTooltip(
                this,
                "选择左/中/右斗尖会发送 0x10 测点，TCU 回 0x90 后自动填入测量值(高度)与经纬度。"
                        + "高度定点：填挖量、设计高程可编辑，预览为设计面高程。"
                        + "坐标定点：目标经纬度高可再改。两种模式均依赖顶栏「已连接」。"
        );
        helpTooltip.attach(btnLevelHelp);

        btnLevelNext.setOnClickListener(v -> proceedToPrecheck());
    }

    private void enterLevelFeatureIfNeeded() {
        if (workflow.isFeatureActive()) {
            if (!LevelTaskState.hasSurveyHeight()) {
                requestSurveyForSelectedRef();
            }
            return;
        }
        setWorkflowBusy(true, "进入找平…");
        workflow.enterFeature(new LevelTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                setWorkflowBusy(false, null);
                requestSurveyForSelectedRef();
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false, null);
                Toast.makeText(LevelSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void proceedToPrecheck() {
        cacheState();
        if (!LevelTaskState.hasNumericValues()) {
            Toast.makeText(this, "请先完成测点并填写填挖量", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LevelTaskState.hasSurveyHeight()) {
            ensureSurveyThenSubmit();
            return;
        }
        submitLevelParamsAndGo();
    }

    /**
     * 协议要求：即使用「高度定点」，也需先完成 TCU 测点(0x10/0x90) 得到参考点高程，
     * 再叠加用户填写的距离/填挖量下发 0x11。未测点时点击下一步会自动补测。
     */
    private void ensureSurveyThenSubmit() {
        if (workflowBusy) {
            return;
        }
        if (!TcuLinkHub.isConnected()) {
            Toast.makeText(this,
                    "接收机未连接，无法测点。请返回主页确认顶部为「已连接」后再试。",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (workflow.getPhase() == LevelTcuWorkflow.Phase.IDLE) {
            setWorkflowBusy(true, null);
            workflow.enterFeature(new LevelTcuWorkflow.StepCallback() {
                @Override
                public void onSuccess() {
                    runSurveyThenSubmit();
                }

                @Override
                public void onError(String message) {
                    setWorkflowBusy(false, null);
                    Toast.makeText(LevelSettingActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        runSurveyThenSubmit();
    }

    private void runSurveyThenSubmit() {
        setWorkflowBusy(true, null);
        workflow.requestSurvey(selectedRef, new LevelTcuWorkflow.SurveyCallback() {
            @Override
            public void onSurveyResult(double heightM, double lat, double lon) {
                onSurveyStoredFromTcu(heightM, lat, lon);
            }

            @Override
            public void onSuccess() {
                submitLevelParamsAndGo();
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false, null);
                Toast.makeText(LevelSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void submitLevelParamsAndGo() {
        setWorkflowBusy(true, null);
        workflow.submitLevelParams(new LevelTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                setWorkflowBusy(false, null);
                startActivity(new Intent(LevelSettingActivity.this, LevelPrecheckActivity.class));
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false, null);
                Toast.makeText(LevelSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void exitLevelAndFinish() {
        workflow.cancelPending();
        setWorkflowBusy(false, null);
        if (!workflow.isFeatureActive()) {
            navigateToMain();
            return;
        }
        workflow.exitFeature(new LevelTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                navigateToMain();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LevelSettingActivity.this, message, Toast.LENGTH_SHORT).show();
                navigateToMain();
            }
        });
    }

    private void setWorkflowBusy(boolean busy, @Nullable String nextLabel) {
        workflowBusy = busy;
        if (btnLevelNext != null) {
            btnLevelNext.setEnabled(!busy);
        }
        refreshSurveyMeasurementDisplay();
        if (!busy || !LevelTaskState.hasSurveyHeight()) {
            refreshSurveyLatLonDisplay();
        }
    }

}
