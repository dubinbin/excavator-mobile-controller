package com.capstone.excavator;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class DitchSideWorkSettingActivity extends ScaledAppCompatActivity {

    private View btnBack;
    private View btnHelp;
    private View btnPrev;
    private View btnNext;
    private View rowTopWidth;

    private HelpTooltip helpTooltip;
    private NumpadView numpad;
    private boolean workflowBusy;
    private final DitchTcuWorkflow workflow = DitchTcuWorkflow.getInstance();

    private TextView tvWaveParam1;
    private TextView tvWaveParam2;
    private TextView tvWaveParam3;
    private TextView tvWaveParam4;
    private ImageView imgDitchSectionType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        setContentView(R.layout.activity_ditch_side_work_setting);

        btnBack = findViewById(R.id.btnLevelBack);
        btnHelp = findViewById(R.id.btnLevelHelp);
        btnPrev = findViewById(R.id.btnDitchPrev);
        btnNext = findViewById(R.id.btnDitchNext);
        rowTopWidth = findViewById(R.id.rowTopWidth);

        tvWaveParam1 = findViewById(R.id.tvWaveParam1);
        tvWaveParam2 = findViewById(R.id.tvWaveParam2);
        tvWaveParam3 = findViewById(R.id.tvWaveParam3);
        tvWaveParam4 = findViewById(R.id.tvWaveParam4);
        imgDitchSectionType = findViewById(R.id.imgDitchSectionType);
        restoreInputsFromState();
        applyDitchSectionTypeImage();
        applyDitchTypeFields();

        DitchStepNavigation.bindBackToMain(btnBack, this);

        helpTooltip = new HelpTooltip(this, "这里是帮助提示内容，你可以在此解释横波/侧向参数的含义。");
        helpTooltip.attach(btnHelp);

        numpad = new NumpadView(this);
        setupInputs();

        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                saveCurrentInputs();
                DitchStepNavigation.goToPrevious(this);
            });
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> submitParamsAndGoPrecheck());
        }

        DitchStepNavigation.bindStepBar(this);
    }

    private void restoreInputsFromState() {
        setTextIfPresent(tvWaveParam1, DitchTaskState.getSideParam1());
        setTextIfPresent(tvWaveParam2, DitchTaskState.getSideParam2());
        setTextIfPresent(tvWaveParam3, DitchTaskState.getSideParam3());
        setTextIfPresent(tvWaveParam4, DitchTaskState.getSideParam4());
    }

    private void applyDitchSectionTypeImage() {
        if (imgDitchSectionType == null) {
            return;
        }
        imgDitchSectionType.setImageResource(DitchTaskState.isSquareDitch()
                ? R.drawable.square_i1
                : R.drawable.trapezoid_i1);
    }

    private void applyDitchTypeFields() {
        if (rowTopWidth != null) {
            rowTopWidth.setVisibility(DitchTaskState.isSquareDitch() ? View.GONE : View.VISIBLE);
        }
    }

    private void setupInputs() {
        setupOneInput(tvWaveParam1);
        setupOneInput(tvWaveParam2);
        setupOneInput(tvWaveParam3);
        setupOneInput(tvWaveParam4);
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

    private static String textOf(TextView tv) {
        return tv == null ? "" : tv.getText().toString();
    }

    private void saveCurrentInputs() {
        DitchTaskState.updateSideParams(
                textOf(tvWaveParam1),
                textOf(tvWaveParam2),
                textOf(tvWaveParam3),
                textOf(tvWaveParam4)
        );
    }

    private void submitParamsAndGoPrecheck() {
        saveCurrentInputs();
        if (workflowBusy) {
            return;
        }
        if (!DitchTaskState.canSubmitDitchParams()) {
            Toast.makeText(this, "请完成 A/B 点与沟深、宽度参数", Toast.LENGTH_SHORT).show();
            return;
        }
        if (DitchTaskState.isTcuParamsAccepted()) {
            DitchStepNavigation.goToNext(this);
            return;
        }
        workflowBusy = true;
        if (btnNext != null) {
            btnNext.setEnabled(false);
        }
        workflow.submitDitchParams(new DitchTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                workflowBusy = false;
                if (btnNext != null) {
                    btnNext.setEnabled(true);
                }
                DitchStepNavigation.goToNext(DitchSideWorkSettingActivity.this);
            }

            @Override
            public void onError(String message) {
                workflowBusy = false;
                if (btnNext != null) {
                    btnNext.setEnabled(true);
                }
                Toast.makeText(DitchSideWorkSettingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void setTextIfPresent(TextView tv, String value) {
        if (tv != null && value != null && !value.trim().isEmpty()) {
            tv.setText(value.trim());
        }
    }

    @Override
    protected void onStop() {
        saveCurrentInputs();
        super.onStop();
        if (helpTooltip != null) helpTooltip.dismiss();
        if (numpad != null && numpad.isShowing()) numpad.dismiss();
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
