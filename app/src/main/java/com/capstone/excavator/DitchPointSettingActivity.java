package com.capstone.excavator;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;

/**
 * 挖沟 A/B 点设置页公共逻辑：斗尖选择、高度/坐标定点、TCU 测点。
 */
abstract class DitchPointSettingActivity extends ScaledAppCompatActivity {

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
    @Nullable
    private TextView tvAbDistance;

    private View btnBack;
    private View btnPrev;
    private View btnNext;
    private View btnHelp;
    private HelpTooltip helpTooltip;
    private NumpadView numpad;
    private ImageView imgDitchSectionType;

    private int selectedRef;
    private boolean isHeightMode = true;
    private boolean workflowBusy;
    private final DitchTcuWorkflow workflow = DitchTcuWorkflow.getInstance();

    protected abstract boolean isPointA();

    protected abstract int getSurveyPointId();

    protected abstract int getInitialRef();

    protected abstract boolean isPointReady();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        setContentView(isPointA() ? R.layout.activity_ditch_setting_pointa
                : R.layout.activity_ditch_setting_pointb);

        bindViews();
        selectedRef = getInitialRef();
        isHeightMode = DitchTaskState.isHeightMode();
        numpad = new NumpadView(this);

        restoreFromState();
        setupRefCards();
        setupModeToggle();
        setupInputs();
        setupActions();
        DitchStepNavigation.bindStepBar(this);
        applyDitchSectionTypeImage();
        refreshUiForCurrentMode();

        if (isPointA() && btnBack != null) {
            btnBack.post(this::enterDitchFeatureIfNeeded);
        } else if (isPointA()) {
            enterDitchFeatureIfNeeded();
        } else if (isHeightMode && !hasSurveyForThisPoint()) {
            requestSurveyForSelectedRef();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUiForCurrentMode();
        if (!isPointA()) {
            refreshAbDistanceDisplay();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        DitchTcuWorkflow.setSurveyStoredListener(this::onSurveyStoredFromTcu);
        refreshUiForCurrentMode();
    }

    @Override
    protected void onStop() {
        cachePointFields();
        DitchTcuWorkflow.setSurveyStoredListener(null);
        super.onStop();
        if (helpTooltip != null) {
            helpTooltip.dismiss();
        }
        if (numpad != null && numpad.isShowing()) {
            numpad.dismiss();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setFullScreenMode();
        }
    }

    private void bindViews() {
        cardRefLeft = findViewById(R.id.cardRefLeftA);
        cardRefMiddle = findViewById(R.id.cardRefMiddleA);
        cardRefRight = findViewById(R.id.cardRefRightA);
        tvRefLeft = findViewById(R.id.tvRefLeftA);
        tvRefMiddle = findViewById(R.id.tvRefMiddleA);
        tvRefRight = findViewById(R.id.tvRefRightA);

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

        btnBack = findViewById(R.id.btnLevelBack);
        btnPrev = findViewById(R.id.btnDitchPrev);
        btnNext = findViewById(R.id.btnLevelNext);
        btnHelp = findViewById(R.id.btnLevelHelp);
        imgDitchSectionType = findViewById(R.id.imgDitchSectionType);
    }

    private void restoreFromState() {
        selectedRef = isPointA() ? DitchTaskState.getRefA() : DitchTaskState.getRefB();
        if (isPointA()) {
            setTextIfNonEmpty(tvTargetHeight, DitchTaskState.getTargetHeightA());
            setTextIfNonEmpty(tvFillCut, DitchTaskState.getFillCutA());
            setTextIfNonEmpty(tvCoordX, DitchTaskState.getTargetLonA());
            setTextIfNonEmpty(tvCoordY, DitchTaskState.getTargetLatA());
            setTextIfNonEmpty(tvCoordZ, DitchTaskState.getFillCutCoordA());
        } else {
            setTextIfNonEmpty(tvTargetHeight, DitchTaskState.getTargetHeightB());
            setTextIfNonEmpty(tvFillCut, DitchTaskState.getFillCutB());
            setTextIfNonEmpty(tvCoordX, DitchTaskState.getTargetLonB());
            setTextIfNonEmpty(tvCoordY, DitchTaskState.getTargetLatB());
            setTextIfNonEmpty(tvCoordZ, DitchTaskState.getFillCutCoordB());
        }
        applyRefSelection();
        applyModeSelection();
        if (tvAbDistance != null && !DitchTaskState.getAbDistance().isEmpty()) {
            tvAbDistance.setText(DitchTaskState.getAbDistance());
        }
    }

    private void cachePointFields() {
        if (isPointA()) {
            DitchTaskState.updatePointA(
                    selectedRef,
                    textOf(tvTargetHeight),
                    textOf(tvFillCut),
                    textOf(tvCoordX),
                    textOf(tvCoordY),
                    textOf(tvCoordZ));
            DitchTaskState.updateBase(
                    DitchTaskState.getDitchType(),
                    selectedRef,
                    DitchTaskState.getRefB(),
                    DitchTaskState.getAbDistance());
        } else {
            DitchTaskState.updatePointB(
                    selectedRef,
                    textOf(tvTargetHeight),
                    textOf(tvFillCut),
                    textOf(tvCoordX),
                    textOf(tvCoordY),
                    textOf(tvCoordZ));
            DitchTaskState.updateBase(
                    DitchTaskState.getDitchType(),
                    DitchTaskState.getRefA(),
                    selectedRef,
                    textOf(tvAbDistance));
        }
    }

    private static void setTextIfNonEmpty(TextView tv, String value) {
        if (tv != null && value != null && !value.isEmpty()) {
            tv.setText(value);
        }
    }

    private void setupRefCards() {
        cardRefLeft.setOnClickListener(v -> selectRef(DitchTaskState.REF_LEFT));
        cardRefMiddle.setOnClickListener(v -> selectRef(DitchTaskState.REF_MIDDLE));
        cardRefRight.setOnClickListener(v -> selectRef(DitchTaskState.REF_RIGHT));
    }

    /**
     * 切换斗尖：与找平 {@link LevelSettingActivity#selectRef} 一样走测点请求，
     * 不走 {@link #setMode(boolean)}（setMode 只管高度/坐标定点切换）。
     */
    private void selectRef(int index) {
        selectedRef = index;
        applyRefSelection();
        cachePointFields();
        if (!isHeightMode) {
            return;
        }
        clearSurveyForThisPoint();
        DitchTaskState.setTcuParamsAccepted(false);
        refreshSurveyMeasurementDisplay();
        requestSurveyForSelectedRef();
    }

    private void clearSurveyForThisPoint() {
        if (isPointA()) {
            DitchTaskState.clearSurveyA();
        } else {
            DitchTaskState.clearSurveyB();
        }
    }

    private void applyRefSelection() {
        applyOneRefGroup(selectedRef, cardRefLeft, cardRefMiddle, cardRefRight,
                tvRefLeft, tvRefMiddle, tvRefRight);
    }

    private void applyOneRefGroup(
            int selected,
            View leftCard, View midCard, View rightCard,
            TextView leftTv, TextView midTv, TextView rightTv) {
        leftCard.setBackground(getDrawable(selected == DitchTaskState.REF_LEFT
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        midCard.setBackground(getDrawable(selected == DitchTaskState.REF_MIDDLE
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        rightCard.setBackground(getDrawable(selected == DitchTaskState.REF_RIGHT
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        leftTv.setTextColor(getColor(selected == DitchTaskState.REF_LEFT
                ? R.color.level_selected : R.color.level_unselected));
        midTv.setTextColor(getColor(selected == DitchTaskState.REF_MIDDLE
                ? R.color.level_selected : R.color.level_unselected));
        rightTv.setTextColor(getColor(selected == DitchTaskState.REF_RIGHT
                ? R.color.level_selected : R.color.level_unselected));
    }

    private void setupModeToggle() {
        btnModeHeight.setOnClickListener(v -> setMode(true));
        btnModeCoord.setOnClickListener(v -> setMode(false));
    }

    /** 仅切换高度/坐标定点 UI；测点由 {@link #selectRef(int)} 或首次进入页面触发。 */
    private void setMode(boolean heightMode) {
        isHeightMode = heightMode;
        DitchTaskState.setHeightMode(heightMode);
        applyModeSelection();
        cachePointFields();
        refreshUiForCurrentMode();
        if (heightMode && !hasSurveyForThisPoint()) {
            requestSurveyForSelectedRef();
        }
    }

    private void applyModeSelection() {
        if (isHeightMode) {
            btnModeHeight.setBackground(getDrawable(R.drawable.level_mode_selected_bg));
            btnModeHeight.setTextColor(getColor(R.color.level_selected));
            btnModeHeight.setTypeface(null, android.graphics.Typeface.BOLD);
            btnModeCoord.setBackground(null);
            btnModeCoord.setTextColor(getColor(R.color.level_unselected));
            btnModeCoord.setTypeface(null, android.graphics.Typeface.NORMAL);
            panelHeightMode.setVisibility(View.VISIBLE);
            panelCoordMode.setVisibility(View.GONE);
        } else {
            btnModeCoord.setBackground(getDrawable(R.drawable.level_mode_selected_bg));
            btnModeCoord.setTextColor(getColor(R.color.level_selected));
            btnModeCoord.setTypeface(null, android.graphics.Typeface.BOLD);
            btnModeHeight.setBackground(null);
            btnModeHeight.setTextColor(getColor(R.color.level_unselected));
            btnModeHeight.setTypeface(null, android.graphics.Typeface.NORMAL);
            panelHeightMode.setVisibility(View.GONE);
            panelCoordMode.setVisibility(View.VISIBLE);
        }
    }

    private void setupInputs() {
        tvTargetHeight.setOnClickListener(v -> showNumpad(tvTargetHeight, this::onHeightInputsChanged));
        if (tvFillCut != null) {
            tvFillCut.setClickable(false);
            tvFillCut.setFocusable(false);
        }
        tvCoordX.setOnClickListener(v -> showNumpad(tvCoordX, this::onCoordInputsChanged));
        tvCoordY.setOnClickListener(v -> showNumpad(tvCoordY, this::onCoordInputsChanged));
        tvCoordZ.setOnClickListener(v -> showNumpad(tvCoordZ, this::onCoordInputsChanged));
        if (tvAbDistance != null) {
            tvAbDistance.setOnClickListener(v -> showNumpad(tvAbDistance, () -> {
                DitchTaskState.setAbDistanceManual(true);
                cachePointFields();
            }));
        }
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
        if (!isPointA() && DitchTaskState.isHeightMode()) {
            DitchTaskState.recomputeAbDistance();
            refreshAbDistanceDisplay();
        }
    }

    private void onCoordInputsChanged() {
        refreshCoordLatLonSummary();
        cachePointFields();
        if (!DitchTaskState.isHeightMode()) {
            DitchTaskState.setAbDistanceManual(false);
            DitchTaskState.recomputeAbDistance();
            if (!isPointA()) {
                refreshAbDistanceDisplay();
            }
        }
    }

    private void syncFillCutFromTargetHeight() {
        if (tvFillCut == null) {
            return;
        }
        Double measurement = getSurveyMeasurementM();
        Double target = DitchTaskState.parseMeters(textOf(tvTargetHeight));
        if (measurement == null || target == null) {
            return;
        }
        tvFillCut.setText(DitchTaskState.formatMeters(target - measurement));
    }

    private void refreshUiForCurrentMode() {
        if (isHeightMode) {
            refreshSurveyMeasurementDisplay();
            syncFillCutFromTargetHeight();
            cachePointFields();
        } else {
            refreshCoordLatLonSummary();
        }
        if (!isPointA()) {
            refreshAbDistanceDisplay();
        }
    }

    private void refreshCoordLatLonSummary() {
        Double lat = DitchTaskState.parseMeters(textOf(tvCoordY));
        Double lon = DitchTaskState.parseMeters(textOf(tvCoordX));
        if (lat != null && lon != null) {
            tvCurrentLatLon.setText(String.format(Locale.US, "%.9f, %.9f", lat, lon));
        } else {
            tvCurrentLatLon.setText("--");
        }
    }

    private void refreshSurveyMeasurementDisplay() {
        System.out.println("DitchPointSettingActivity: refreshSurveyMeasurementDisplay: hasSurveyForThisPoint=" + hasSurveyForThisPoint() + ", surveyMeasurementM=" + getSurveyMeasurementM());
        if (hasSurveyForThisPoint()) {
            tvCurrentRef.setText(DitchTaskState.formatMeters(getSurveyMeasurementM()));
            tvCurrentRef.setTextColor(getColor(R.color.level_selected));
            return;
        }
        tvCurrentRef.setTextColor(getColor(R.color.level_unselected));
        tvCurrentRef.setText(workflowBusy ? "测点中…" : "--");
    }

    private void refreshAbDistanceDisplay() {
        if (tvAbDistance == null) {
            return;
        }
        String dist = DitchTaskState.getAbDistance();
        tvAbDistance.setText(dist.isEmpty() ? "--" : dist);
    }

    private void onSurveyStoredFromTcu(int pointId, double heightM, double lat, double lon) {
        if (pointId != getSurveyPointId()) {
            return;
        }
        workflowBusy = false;
        if (btnNext != null) {
            btnNext.setEnabled(true);
        }
        refreshSurveyMeasurementDisplay();
        syncFillCutFromTargetHeight();
        cachePointFields();
        if (!isPointA()) {
            DitchTaskState.setAbDistanceManual(false);
            DitchTaskState.recomputeAbDistance();
            refreshAbDistanceDisplay();
        }
    }

    private void setupActions() {
        DitchStepNavigation.bindBackToMain(btnBack, this);
        helpTooltip = new HelpTooltip(
                this,
                (isPointA() ? "A" : "B") + " 点：高度定点选斗尖测点(0x10)，目标高度可编辑，填挖量=目标高度−当前参考点。"
                        + "坐标定点：手动输入经纬度与填挖量。"
                        + (isPointA() ? "" : " AB 距离由 A/B 坐标自动计算，也可手动修改。")
        );
        helpTooltip.attach(btnHelp);
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                cachePointFields();
                DitchStepNavigation.goToPrevious(this);
            });
        }
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> proceedToNextStep());
        }
    }

    private void proceedToNextStep() {
        cachePointFields();
        if (!isPointReady()) {
            if (isHeightMode && !hasSurveyForThisPoint()) {
                ensureSurveyThenProceed();
                return;
            }
            Toast.makeText(this,
                    isHeightMode ? "请先完成测点并填写目标高度" : "请填写目标经纬度与填挖量",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        DitchStepNavigation.goToNext(this);
    }

    private void enterDitchFeatureIfNeeded() {
        if (workflow.isFeatureActive()) {
            if (isHeightMode && !hasSurveyForThisPoint()) {
                requestSurveyForSelectedRef();
            }
            return;
        }
        setWorkflowBusy(true);
        workflow.enterFeature(new DitchTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                setWorkflowBusy(false);
                if (isHeightMode) {
                    requestSurveyForSelectedRef();
                }
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false);
                Toast.makeText(DitchPointSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void ensureSurveyThenProceed() {
        if (workflowBusy) {
            return;
        }
        if (!TcuLinkHub.isConnected()) {
            Toast.makeText(this, "接收机未连接，请返回主页确认「已连接」后再试。", Toast.LENGTH_LONG).show();
            return;
        }
        if (workflow.getPhase() == DitchTcuWorkflow.Phase.IDLE) {
            setWorkflowBusy(true);
            workflow.enterFeature(new DitchTcuWorkflow.StepCallback() {
                @Override
                public void onSuccess() {
                    runSurveyThenProceed();
                }

                @Override
                public void onError(String message) {
                    setWorkflowBusy(false);
                    Toast.makeText(DitchPointSettingActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        runSurveyThenProceed();
    }

    private void runSurveyThenProceed() {
        setWorkflowBusy(true);
        requestSurveyInternal(new DitchTcuWorkflow.SurveyCallback() {
            @Override
            public void onSurveyResult(double heightM, double lat, double lon) {
                onSurveyStoredFromTcu(getSurveyPointId(), heightM, lat, lon);
            }

            @Override
            public void onSuccess() {
                setWorkflowBusy(false);
                if (isPointReady()) {
                    DitchStepNavigation.goToNext(DitchPointSettingActivity.this);
                }
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false);
                Toast.makeText(DitchPointSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestSurveyForSelectedRef() {
        if (workflowBusy || !isHeightMode) {
            return;
        }
        setWorkflowBusy(true);
        requestSurveyInternal(new DitchTcuWorkflow.SurveyCallback() {
            @Override
            public void onSurveyResult(double heightM, double lat, double lon) {
                onSurveyStoredFromTcu(getSurveyPointId(), heightM, lat, lon);
            }

            @Override
            public void onSuccess() {
                setWorkflowBusy(false);
                Toast.makeText(DitchPointSettingActivity.this,
                        (isPointA() ? "A" : "B") + " 点测点成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false);
                Toast.makeText(DitchPointSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestSurveyInternal(DitchTcuWorkflow.SurveyCallback callback) {
        if (isPointA()) {
            workflow.requestSurveyA(selectedRef, callback);
        } else {
            workflow.requestSurveyB(selectedRef, callback);
        }
    }

    private boolean hasSurveyForThisPoint() {
        return isPointA() ? DitchTaskState.hasSurveyA() : DitchTaskState.hasSurveyB();
    }

    @Nullable
    private Double getSurveyMeasurementM() {
        return isPointA() ? DitchTaskState.getSurveyAHeightM() : DitchTaskState.getSurveyBHeightM();
    }

    private void setWorkflowBusy(boolean busy) {
        workflowBusy = busy;
        if (btnNext != null) {
            btnNext.setEnabled(!busy);
        }
        refreshUiForCurrentMode();
    }

    private void applyDitchSectionTypeImage() {
        if (imgDitchSectionType == null) {
            return;
        }
        if (isPointA()) {
            imgDitchSectionType.setImageResource(DitchTaskState.isSquareDitch()
                    ? R.drawable.ditch_step2a1 : R.drawable.ditch_step2a);
        } else {
            imgDitchSectionType.setImageResource(DitchTaskState.isSquareDitch()
                    ? R.drawable.ditch_step2b1 : R.drawable.ditch_step2b);
        }
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

    private static String textOf(TextView tv) {
        return tv == null ? "" : tv.getText().toString();
    }
}
