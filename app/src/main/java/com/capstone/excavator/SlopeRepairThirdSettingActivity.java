package com.capstone.excavator;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SlopeRepairThirdSettingActivity extends ScaledAppCompatActivity {

    private View btnBack;
    private View btnHelp;
    private View btnPrev;
    private View btnNext;

    private HelpTooltip helpTooltip;
    private NumpadView numpad;
    private boolean workflowBusy;
    private final SlopeRepairTcuWorkflow workflow = SlopeRepairTcuWorkflow.getInstance();

    private LinearLayout cardRefLeftC, cardRefMiddleC, cardRefRightC;
    private TextView tvRefLeftC, tvRefMiddleC, tvRefRightC;
    private int selectedRefC = 1;

    private TextView tvSlopeRatio;
    private TextView tvVerticalH;
    private TextView tvHorizontalL;

    private TextView btnSlopeDirLeft;
    private TextView btnSlopeDirRight;
    private boolean slopeDirRight = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        setContentView(R.layout.activity_slope_repair_third_setting);

        bindViews();
        restoreFromState();
        setupHelp();
        setupRefCards();
        setupSlopeDirToggle();
        setupInputs();
        setupActions();
        SlopeRepairStepNavigation.bindStepBar(this);
        if (!SlopeRepairTaskState.hasSurvey(TcuBusinessCodec.POINT_C)) {
            requestSurveyForSelectedRef();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SlopeRepairTcuWorkflow.setSurveyStoredListener(this::onSurveyStoredFromTcu);
    }

    @Override
    protected void onStop() {
        saveCurrentState();
        SlopeRepairTcuWorkflow.setSurveyStoredListener(null);
        super.onStop();
        if (helpTooltip != null) helpTooltip.dismiss();
        if (numpad != null && numpad.isShowing()) numpad.dismiss();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnLevelBack);
        btnHelp = findViewById(R.id.btnLevelHelp);
        btnPrev = findViewById(R.id.btnDitchPrev);
        btnNext = findViewById(R.id.btnSlopeRepairNext);

        // Layout uses A-suffixed ids, but this page labels it as C 点.
        cardRefLeftC = findViewById(R.id.cardRefLeftA);
        cardRefMiddleC = findViewById(R.id.cardRefMiddleA);
        cardRefRightC = findViewById(R.id.cardRefRightA);
        tvRefLeftC = findViewById(R.id.tvRefLeftA);
        tvRefMiddleC = findViewById(R.id.tvRefMiddleA);
        tvRefRightC = findViewById(R.id.tvRefRightA);

        tvSlopeRatio = findViewById(R.id.tvAbDistance);
        tvVerticalH = findViewById(R.id.tvAbLift);
        tvHorizontalL = findViewById(R.id.tvAbHeightDiff);

        btnSlopeDirLeft = findViewById(R.id.btnSlopeDirLeft);
        btnSlopeDirRight = findViewById(R.id.btnSlopeDirRight);

        numpad = new NumpadView(this);
    }

    private void restoreFromState() {
        selectedRefC = SlopeRepairTaskState.getRefC();
        slopeDirRight = SlopeRepairTaskState.isSlopeDirectionRight();
        setTextIfPresent(tvSlopeRatio, SlopeRepairTaskState.getSlopeRatio());
        setTextIfPresent(tvVerticalH, SlopeRepairTaskState.getVerticalHeight());
        setTextIfPresent(tvHorizontalL, SlopeRepairTaskState.getHorizontalDistance());
    }

    private void setupHelp() {
        helpTooltip = new HelpTooltip(this, "这里是帮助提示内容，你可以在此解释修坡参数与坡面方向含义。");
        helpTooltip.attach(btnHelp);
    }

    private void setupRefCards() {
        if (cardRefLeftC != null) cardRefLeftC.setOnClickListener(v -> setRefC(0));
        if (cardRefMiddleC != null) cardRefMiddleC.setOnClickListener(v -> setRefC(1));
        if (cardRefRightC != null) cardRefRightC.setOnClickListener(v -> setRefC(2));
        applyRefSelectionC();
    }

    private void setRefC(int index) {
        selectedRefC = index;
        applyRefSelectionC();
        saveCurrentState();
        SlopeRepairTaskState.clearSurvey(TcuBusinessCodec.POINT_C);
        requestSurveyForSelectedRef();
    }

    private void applyRefSelectionC() {
        applyOneRefGroup(selectedRefC, cardRefLeftC, cardRefMiddleC, cardRefRightC,
                tvRefLeftC, tvRefMiddleC, tvRefRightC);
    }

    private void applyOneRefGroup(
            int selected,
            View leftCard, View midCard, View rightCard,
            TextView leftTv, TextView midTv, TextView rightTv
    ) {
        if (leftCard != null) leftCard.setBackground(getDrawable(selected == 0
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        if (midCard != null) midCard.setBackground(getDrawable(selected == 1
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));
        if (rightCard != null) rightCard.setBackground(getDrawable(selected == 2
                ? R.drawable.level_card_selected_bg : R.drawable.level_card_normal_bg));

        if (leftTv != null) leftTv.setTextColor(getColor(selected == 0 ? R.color.level_selected : R.color.level_unselected));
        if (midTv != null) midTv.setTextColor(getColor(selected == 1 ? R.color.level_selected : R.color.level_unselected));
        if (rightTv != null) rightTv.setTextColor(getColor(selected == 2 ? R.color.level_selected : R.color.level_unselected));
    }

    private void setupSlopeDirToggle() {
        if (btnSlopeDirLeft != null) btnSlopeDirLeft.setOnClickListener(v -> setSlopeDir(false));
        if (btnSlopeDirRight != null) btnSlopeDirRight.setOnClickListener(v -> setSlopeDir(true));
        applySlopeDir();
    }

    private void setSlopeDir(boolean right) {
        slopeDirRight = right;
        applySlopeDir();
    }

    private void applySlopeDir() {
        if (btnSlopeDirLeft != null) {
            btnSlopeDirLeft.setBackgroundResource(slopeDirRight
                    ? R.drawable.slope_dir_unselected_bg
                    : R.drawable.slope_dir_selected_bg);
            btnSlopeDirLeft.setTextColor(getColor(slopeDirRight ? R.color.level_unselected : R.color.level_selected));
            btnSlopeDirLeft.setTypeface(null, slopeDirRight ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        }
        if (btnSlopeDirRight != null) {
            btnSlopeDirRight.setBackgroundResource(slopeDirRight
                    ? R.drawable.slope_dir_selected_bg
                    : R.drawable.slope_dir_unselected_bg);
            btnSlopeDirRight.setTextColor(getColor(slopeDirRight ? R.color.level_selected : R.color.level_unselected));
            btnSlopeDirRight.setTypeface(null, slopeDirRight ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void setupInputs() {
        setupOneInput(tvSlopeRatio);
        setupOneInput(tvVerticalH);
        setupOneInput(tvHorizontalL);
    }

    private void setupOneInput(TextView tv) {
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            if (numpad != null && numpad.isShowing()) {
                numpad.dismiss();
                return;
            }
            if (numpad == null) return;
            numpad.setOnConfirmListener(tv::setText);
            numpad.showForAtScreen(tv, tv,
                    NumpadPositionConfig.SCREEN_X, NumpadPositionConfig.SCREEN_Y);
        });
    }

    private void setupActions() {
        SlopeRepairStepNavigation.bindBackToMain(btnBack, this);

        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                saveCurrentState();
                SlopeRepairStepNavigation.goToPrevious(this);
            });
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                saveCurrentState();
                submitParamsAndGoPrecheck();
            });
        }
    }

    private void requestSurveyForSelectedRef() {
        if (workflowBusy) {
            return;
        }
        if (workflow.getPhase() == SlopeRepairTcuWorkflow.Phase.IDLE) {
            setWorkflowBusy(true);
            workflow.enterFeature(new SlopeRepairTcuWorkflow.StepCallback() {
                @Override
                public void onSuccess() {
                    runSurveyC();
                }

                @Override
                public void onError(String message) {
                    setWorkflowBusy(false);
                    Toast.makeText(SlopeRepairThirdSettingActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        runSurveyC();
    }

    private void runSurveyC() {
        setWorkflowBusy(true);
        workflow.requestSurvey(TcuBusinessCodec.POINT_C, selectedRefC, new SlopeRepairTcuWorkflow.SurveyCallback() {
            @Override
            public void onSurveyResult(double heightM, double lat, double lon) {
                onSurveyStoredFromTcu(TcuBusinessCodec.POINT_C, heightM, lat, lon);
            }

            @Override
            public void onSuccess() {
                setWorkflowBusy(false);
                Toast.makeText(SlopeRepairThirdSettingActivity.this, "C 点测点成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false);
                Toast.makeText(SlopeRepairThirdSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onSurveyStoredFromTcu(int pointId, double heightM, double lat, double lon) {
        if (pointId == TcuBusinessCodec.POINT_C) {
            setWorkflowBusy(false);
        }
    }

    private void submitParamsAndGoPrecheck() {
        if (workflowBusy) {
            return;
        }
        if (!SlopeRepairTaskState.hasSurvey(TcuBusinessCodec.POINT_C)) {
            requestSurveyForSelectedRef();
            return;
        }
        if (!SlopeRepairTaskState.canSubmitSlopeParams()) {
            Toast.makeText(this, "请完成 A/B/C 点与坡比、垂高、平距", Toast.LENGTH_SHORT).show();
            return;
        }
        if (SlopeRepairTaskState.isTcuParamsAccepted()) {
            SlopeRepairStepNavigation.goToNext(this);
            return;
        }
        setWorkflowBusy(true);
        workflow.submitSlopeParams(new SlopeRepairTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                setWorkflowBusy(false);
                SlopeRepairStepNavigation.goToNext(SlopeRepairThirdSettingActivity.this);
            }

            @Override
            public void onError(String message) {
                setWorkflowBusy(false);
                Toast.makeText(SlopeRepairThirdSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setWorkflowBusy(boolean busy) {
        workflowBusy = busy;
        if (btnNext != null) {
            btnNext.setEnabled(!busy);
        }
    }

    private void saveCurrentState() {
        SlopeRepairTaskState.updateThirdStep(
                selectedRefC,
                textOf(tvSlopeRatio),
                textOf(tvVerticalH),
                textOf(tvHorizontalL),
                slopeDirRight
        );
    }

    private static String textOf(TextView tv) {
        return tv == null ? "" : tv.getText().toString();
    }

    private static void setTextIfPresent(TextView tv, String value) {
        if (tv != null && value != null && !value.trim().isEmpty()) {
            tv.setText(value.trim());
        }
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
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
