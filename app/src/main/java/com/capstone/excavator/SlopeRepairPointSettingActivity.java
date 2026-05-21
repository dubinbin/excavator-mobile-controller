package com.capstone.excavator;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;

abstract class SlopeRepairPointSettingActivity extends ScaledAppCompatActivity {

    private LinearLayout cardRefLeft;
    private LinearLayout cardRefMiddle;
    private LinearLayout cardRefRight;
    private TextView tvRefLeft;
    private TextView tvRefMiddle;
    private TextView tvRefRight;

    private TextView btnModeHeight;
    private TextView btnModeCoord;
    private View panelHeightMode;
    private View panelCoordMode;

    private TextView tvCurrentRef;
    private TextView tvTargetHeight;
    private TextView tvFillCut;
    private TextView tvCoordX;
    private TextView tvCoordY;
    private TextView tvCoordZ;
    private TextView tvCurrentLatLon;
    private TextView tvAbDistance;
    private TextView tvAbLift;
    private TextView tvAbHeightDiff;

    private View btnBack;
    private View btnPrev;
    private View btnNext;
    private View btnHelp;
    private HelpTooltip helpTooltip;
    private NumpadView numpad;
    private int selectedRef;
    private boolean heightMode;
    private boolean workflowBusy;
    private final SlopeRepairTcuWorkflow workflow = SlopeRepairTcuWorkflow.getInstance();

    protected abstract boolean isPointA();
    protected abstract int getSurveyPointId();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        setContentView(isPointA()
                ? R.layout.activity_slope_repair_second_setting_pointa
                : R.layout.activity_slope_repair_second_setting_pointb);

        bindViews();
        selectedRef = isPointA() ? SlopeRepairTaskState.getRefA() : SlopeRepairTaskState.getRefB();
        heightMode = SlopeRepairTaskState.isHeightMode();
        numpad = new NumpadView(this);
        restoreFromState();
        setupRefCards();
        setupModeToggle();
        setupInputs();
        setupActions();
        SlopeRepairStepNavigation.bindStepBar(this);
        refreshUiForCurrentMode();

        if (isPointA() && btnBack != null) {
            btnBack.post(this::enterSlopeFeatureIfNeeded);
        } else if (isPointA()) {
            enterSlopeFeatureIfNeeded();
        } else if (heightMode && !hasSurveyForThisPoint()) {
            requestSurveyForSelectedRef();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SlopeRepairTcuWorkflow.setSurveyStoredListener(this::onSurveyStoredFromTcu);
        refreshUiForCurrentMode();
    }

    @Override
    protected void onStop() {
        cachePointFields();
        SlopeRepairTcuWorkflow.setSurveyStoredListener(null);
        super.onStop();
        if (helpTooltip != null) helpTooltip.dismiss();
        if (numpad != null && numpad.isShowing()) numpad.dismiss();
    }

    private void bindViews() {
        if (isPointA()) {
            cardRefLeft = findViewById(R.id.cardRefLeftA);
            cardRefMiddle = findViewById(R.id.cardRefMiddleA);
            cardRefRight = findViewById(R.id.cardRefRightA);
            tvRefLeft = findViewById(R.id.tvRefLeftA);
            tvRefMiddle = findViewById(R.id.tvRefMiddleA);
            tvRefRight = findViewById(R.id.tvRefRightA);
        } else {
            cardRefLeft = findViewById(R.id.cardRefLeftB);
            cardRefMiddle = findViewById(R.id.cardRefMiddleB);
            cardRefRight = findViewById(R.id.cardRefRightB);
            tvRefLeft = findViewById(R.id.tvRefLeftB);
            tvRefMiddle = findViewById(R.id.tvRefMiddleB);
            tvRefRight = findViewById(R.id.tvRefRightB);
        }
        btnModeHeight = findViewById(R.id.btnModeHeight);
        btnModeCoord = findViewById(R.id.btnModeCoord);
        panelHeightMode = findViewById(R.id.panelHeightMode);
        panelCoordMode = findViewById(R.id.panelCoordMode);
        tvCurrentRef = findViewById(R.id.tvCurrentRef);
        tvTargetHeight = findViewById(R.id.tvTargetHeight);
        tvFillCut = findViewById(R.id.tvFillCut);
        tvCoordX = findViewById(R.id.tvCoordX);
        tvCoordY = findViewById(R.id.tvCoordY);
        tvCoordZ = findViewById(R.id.tvCoordZ);
        tvCurrentLatLon = findViewById(R.id.tvCurrentLatLon);
        tvAbDistance = findViewById(R.id.tvAbDistance);
        tvAbLift = findViewById(R.id.tvAbLift);
        tvAbHeightDiff = findViewById(R.id.tvAbHeightDiff);
        btnBack = findViewById(R.id.btnLevelBack);
        btnPrev = findViewById(R.id.btnDitchPrev);
        btnNext = findViewById(R.id.btnSlopeRepairNext);
        btnHelp = findViewById(R.id.btnLevelHelp);
    }

    private void restoreFromState() {
        if (isPointA()) {
            setTextIfNonEmpty(tvTargetHeight, SlopeRepairTaskState.getTargetHeightA());
            setTextIfNonEmpty(tvFillCut, SlopeRepairTaskState.getFillCutA());
            setTextIfNonEmpty(tvCoordX, SlopeRepairTaskState.getTargetLonA());
            setTextIfNonEmpty(tvCoordY, SlopeRepairTaskState.getTargetLatA());
            setTextIfNonEmpty(tvCoordZ, SlopeRepairTaskState.getFillCutCoordA());
        } else {
            setTextIfNonEmpty(tvTargetHeight, SlopeRepairTaskState.getTargetHeightB());
            setTextIfNonEmpty(tvFillCut, SlopeRepairTaskState.getFillCutB());
            setTextIfNonEmpty(tvCoordX, SlopeRepairTaskState.getTargetLonB());
            setTextIfNonEmpty(tvCoordY, SlopeRepairTaskState.getTargetLatB());
            setTextIfNonEmpty(tvCoordZ, SlopeRepairTaskState.getFillCutCoordB());
            setTextIfNonEmpty(tvAbDistance, SlopeRepairTaskState.getAbDistance());
            setTextIfNonEmpty(tvAbLift, SlopeRepairTaskState.getAbLift());
            setTextIfNonEmpty(tvAbHeightDiff, SlopeRepairTaskState.getAbHeightDiff());
        }
        applyRefSelection();
        applyModeSelection();
    }

    private void setupRefCards() {
        if (cardRefLeft != null) cardRefLeft.setOnClickListener(v -> selectRef(SlopeRepairTaskState.REF_LEFT));
        if (cardRefMiddle != null) cardRefMiddle.setOnClickListener(v -> selectRef(SlopeRepairTaskState.REF_MIDDLE));
        if (cardRefRight != null) cardRefRight.setOnClickListener(v -> selectRef(SlopeRepairTaskState.REF_RIGHT));
    }

    private void selectRef(int ref) {
        selectedRef = ref;
        applyRefSelection();
        cachePointFields();
        if (heightMode) {
            SlopeRepairTaskState.clearSurvey(getSurveyPointId());
            requestSurveyForSelectedRef();
        }
    }

    private void setupModeToggle() {
        if (btnModeHeight != null) btnModeHeight.setOnClickListener(v -> setMode(true));
        if (btnModeCoord != null) btnModeCoord.setOnClickListener(v -> setMode(false));
    }

    private void setMode(boolean height) {
        heightMode = height;
        SlopeRepairTaskState.setHeightMode(height);
        applyModeSelection();
        cachePointFields();
        refreshUiForCurrentMode();
        if (height && !hasSurveyForThisPoint()) {
            requestSurveyForSelectedRef();
        }
    }

    private void setupInputs() {
        if (tvTargetHeight != null) tvTargetHeight.setOnClickListener(v -> showNumpad(tvTargetHeight, this::onHeightInputsChanged));
        if (tvFillCut != null) {
            tvFillCut.setClickable(false);
            tvFillCut.setFocusable(false);
        }
        if (tvCoordX != null) tvCoordX.setOnClickListener(v -> showNumpad(tvCoordX, this::onCoordInputsChanged));
        if (tvCoordY != null) tvCoordY.setOnClickListener(v -> showNumpad(tvCoordY, this::onCoordInputsChanged));
        if (tvCoordZ != null) tvCoordZ.setOnClickListener(v -> showNumpad(tvCoordZ, this::onCoordInputsChanged));
        setReadOnly(tvAbDistance);
        setReadOnly(tvAbLift);
        setReadOnly(tvAbHeightDiff);
    }

    private void showNumpad(TextView target, Runnable onConfirm) {
        if (numpad.isShowing()) {
            numpad.dismiss();
            return;
        }
        numpad.setOnConfirmListener(value -> {
            target.setText(value);
            onConfirm.run();
        });
        numpad.showForAtScreen(target, target,
                NumpadPositionConfig.SCREEN_X, NumpadPositionConfig.SCREEN_Y);
    }

    private void onHeightInputsChanged() {
        syncFillCutFromTargetHeight();
        cachePointFields();
    }

    private void onCoordInputsChanged() {
        refreshCoordLatLonSummary();
        cachePointFields();
    }

    private void syncFillCutFromTargetHeight() {
        Double measurement = getSurveyMeasurementM();
        Double target = SlopeRepairTaskState.parseMeters(textOf(tvTargetHeight));
        if (tvFillCut != null && measurement != null && target != null && !Double.isNaN(target)) {
            tvFillCut.setText(SlopeRepairTaskState.formatMeters(target - measurement));
        }
    }

    private void setupActions() {
        SlopeRepairStepNavigation.bindBackToMain(btnBack, this);
        helpTooltip = new HelpTooltip(this,
                (isPointA() ? "A" : "B") + " 点：高度定点需测点，填挖量=目标高度−当前参考点；坐标定点手动输入经纬度与高程。");
        helpTooltip.attach(btnHelp);
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                cachePointFields();
                SlopeRepairStepNavigation.goToPrevious(this);
            });
        }
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> proceedToNextStep());
        }
    }

    private void proceedToNextStep() {
        cachePointFields();
        if (!SlopeRepairTaskState.isPointReady(getSurveyPointId())) {
            if (heightMode && !hasSurveyForThisPoint()) {
                ensureSurveyThenProceed();
                return;
            }
            Toast.makeText(this,
                    heightMode ? "请先完成测点并填写目标高度" : "请填写目标经纬度与高程",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isPointA() && !hasAbMetrics()) {
            Toast.makeText(this, "请先完成 A/B 点测点以计算 AB 距离与升降量", Toast.LENGTH_SHORT).show();
            return;
        }
        SlopeRepairStepNavigation.goToNext(this);
    }

    private void enterSlopeFeatureIfNeeded() {
        if (workflow.isFeatureActive()) {
            if (heightMode && !hasSurveyForThisPoint()) requestSurveyForSelectedRef();
            return;
        }
        setWorkflowBusy(true);
        workflow.enterFeature(new SlopeRepairTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                setWorkflowBusy(false);
                if (heightMode) requestSurveyForSelectedRef();
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false);
                Toast.makeText(SlopeRepairPointSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void ensureSurveyThenProceed() {
        if (workflowBusy) return;
        if (!TcuLinkHub.isConnected()) {
            Toast.makeText(this, "接收机未连接，请返回主页确认「已连接」后再试。", Toast.LENGTH_LONG).show();
            return;
        }
        if (workflow.getPhase() == SlopeRepairTcuWorkflow.Phase.IDLE) {
            setWorkflowBusy(true);
            workflow.enterFeature(new SlopeRepairTcuWorkflow.StepCallback() {
                @Override
                public void onSuccess() {
                    runSurveyThenProceed();
                }

                @Override
                public void onError(String message) {
                    setWorkflowBusy(false);
                    Toast.makeText(SlopeRepairPointSettingActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        runSurveyThenProceed();
    }

    private void runSurveyThenProceed() {
        setWorkflowBusy(true);
        requestSurveyInternal(new SlopeRepairTcuWorkflow.SurveyCallback() {
            @Override
            public void onSurveyResult(double heightM, double lat, double lon) {
                onSurveyStoredFromTcu(getSurveyPointId(), heightM, lat, lon);
            }

            @Override
            public void onSuccess() {
                setWorkflowBusy(false);
                if (SlopeRepairTaskState.isPointReady(getSurveyPointId())) {
                    SlopeRepairStepNavigation.goToNext(SlopeRepairPointSettingActivity.this);
                }
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false);
                Toast.makeText(SlopeRepairPointSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestSurveyForSelectedRef() {
        if (workflowBusy || !heightMode) return;
        setWorkflowBusy(true);
        requestSurveyInternal(new SlopeRepairTcuWorkflow.SurveyCallback() {
            @Override
            public void onSurveyResult(double heightM, double lat, double lon) {
                onSurveyStoredFromTcu(getSurveyPointId(), heightM, lat, lon);
            }

            @Override
            public void onSuccess() {
                setWorkflowBusy(false);
                Toast.makeText(SlopeRepairPointSettingActivity.this,
                        (isPointA() ? "A" : "B") + " 点测点成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false);
                Toast.makeText(SlopeRepairPointSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestSurveyInternal(SlopeRepairTcuWorkflow.SurveyCallback callback) {
        workflow.requestSurvey(getSurveyPointId(), selectedRef, callback);
    }

    private void onSurveyStoredFromTcu(int pointId, double heightM, double lat, double lon) {
        if (pointId != getSurveyPointId()) return;
        setWorkflowBusy(false);
        refreshSurveyMeasurementDisplay();
        syncFillCutFromTargetHeight();
        cachePointFields();
        refreshAbMetricsDisplay();
    }

    private void refreshUiForCurrentMode() {
        if (heightMode) {
            refreshSurveyMeasurementDisplay();
            syncFillCutFromTargetHeight();
            cachePointFields();
            refreshAbMetricsDisplay();
        } else {
            refreshCoordLatLonSummary();
        }
        if (!isPointA()) {
            refreshAbMetricsDisplay();
        }
    }

    private void refreshSurveyMeasurementDisplay() {
        if (tvCurrentRef == null) return;
        if (hasSurveyForThisPoint()) {
            tvCurrentRef.setText(SlopeRepairTaskState.formatMeters(getSurveyMeasurementM()));
            tvCurrentRef.setTextColor(getColor(R.color.level_selected));
        } else {
            tvCurrentRef.setText(workflowBusy ? "测点中…" : "--");
            tvCurrentRef.setTextColor(getColor(R.color.level_unselected));
        }
    }

    private void refreshCoordLatLonSummary() {
        if (tvCurrentLatLon == null) return;
        Double lat = SlopeRepairTaskState.parseMeters(textOf(tvCoordY));
        Double lon = SlopeRepairTaskState.parseMeters(textOf(tvCoordX));
        tvCurrentLatLon.setText(lat != null && lon != null
                ? String.format(Locale.US, "%.9f, %.9f", lat, lon)
                : "--");
    }

    private void cachePointFields() {
        if (isPointA()) {
            SlopeRepairTaskState.updatePointA(selectedRef, textOf(tvTargetHeight), textOf(tvFillCut),
                    textOf(tvCoordX), textOf(tvCoordY), textOf(tvCoordZ));
        } else {
            SlopeRepairTaskState.updatePointB(selectedRef, textOf(tvTargetHeight), textOf(tvFillCut),
                    textOf(tvCoordX), textOf(tvCoordY), textOf(tvCoordZ));
            SlopeRepairTaskState.updateSecondStep(
                    SlopeRepairTaskState.getRefA(),
                    selectedRef,
                    textOf(tvAbDistance),
                    textOf(tvAbLift),
                    textOf(tvAbHeightDiff));
        }
    }

    private boolean hasAbMetrics() {
        return !Double.isNaN(SlopeRepairTaskState.parseMetersValue(SlopeRepairTaskState.getAbDistance()))
                && !Double.isNaN(SlopeRepairTaskState.parseMetersValue(SlopeRepairTaskState.getAbLift()));
    }

    private void refreshAbMetricsDisplay() {
        if (isPointA()) {
            return;
        }
        SlopeRepairTaskState.recomputeAbMetrics();
        setTextOrPlaceholder(tvAbDistance, SlopeRepairTaskState.getAbDistance());
        setTextOrPlaceholder(tvAbLift, SlopeRepairTaskState.getAbLift());
        setTextOrPlaceholder(tvAbHeightDiff, SlopeRepairTaskState.getAbHeightDiff());
    }

    private static void setReadOnly(TextView tv) {
        if (tv != null) {
            tv.setClickable(false);
            tv.setFocusable(false);
        }
    }

    private boolean hasSurveyForThisPoint() {
        return SlopeRepairTaskState.hasSurvey(getSurveyPointId());
    }

    @Nullable
    private Double getSurveyMeasurementM() {
        double v = SlopeRepairTaskState.getSurveyHeightM(getSurveyPointId());
        return Double.isNaN(v) ? null : v;
    }

    private void setWorkflowBusy(boolean busy) {
        workflowBusy = busy;
        if (btnNext != null) btnNext.setEnabled(!busy);
        refreshSurveyMeasurementDisplay();
    }

    private void applyRefSelection() {
        applyOneRefGroup(selectedRef, cardRefLeft, cardRefMiddle, cardRefRight, tvRefLeft, tvRefMiddle, tvRefRight);
    }

    private void applyOneRefGroup(int selected, View leftCard, View midCard, View rightCard,
                                  TextView leftTv, TextView midTv, TextView rightTv) {
        if (leftCard != null) leftCard.setBackground(getDrawable(selected == SlopeRepairTaskState.REF_LEFT
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        if (midCard != null) midCard.setBackground(getDrawable(selected == SlopeRepairTaskState.REF_MIDDLE
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        if (rightCard != null) rightCard.setBackground(getDrawable(selected == SlopeRepairTaskState.REF_RIGHT
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        if (leftTv != null) leftTv.setTextColor(getColor(selected == SlopeRepairTaskState.REF_LEFT ? R.color.level_selected : R.color.level_unselected));
        if (midTv != null) midTv.setTextColor(getColor(selected == SlopeRepairTaskState.REF_MIDDLE ? R.color.level_selected : R.color.level_unselected));
        if (rightTv != null) rightTv.setTextColor(getColor(selected == SlopeRepairTaskState.REF_RIGHT ? R.color.level_selected : R.color.level_unselected));
    }

    private void applyModeSelection() {
        if (btnModeHeight != null) {
            btnModeHeight.setBackground(heightMode ? getDrawable(R.drawable.level_mode_selected_bg) : null);
            btnModeHeight.setTextColor(getColor(heightMode ? R.color.level_selected : R.color.level_unselected));
            btnModeHeight.setTypeface(null, heightMode ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (btnModeCoord != null) {
            btnModeCoord.setBackground(heightMode ? null : getDrawable(R.drawable.level_mode_selected_bg));
            btnModeCoord.setTextColor(getColor(heightMode ? R.color.level_unselected : R.color.level_selected));
            btnModeCoord.setTypeface(null, heightMode ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        }
        if (panelHeightMode != null) panelHeightMode.setVisibility(heightMode ? View.VISIBLE : View.GONE);
        if (panelCoordMode != null) panelCoordMode.setVisibility(heightMode ? View.GONE : View.VISIBLE);
    }

    private static void setTextIfNonEmpty(TextView tv, String value) {
        if (tv != null && value != null && !value.isEmpty()) tv.setText(value);
    }

    private static void setTextOrPlaceholder(TextView tv, String value) {
        if (tv != null) {
            tv.setText(value == null || value.trim().isEmpty() ? "--" : value.trim());
        }
    }

    private static String textOf(TextView tv) {
        return tv == null ? "" : tv.getText().toString();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setFullScreenMode();
    }

    private void setFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
