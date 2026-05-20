package com.capstone.excavator;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class DitchPrecheckActivity extends ScaledAppCompatActivity {

    private View btnBack;
    private TextView btnPrev;
    private TextView btnStart;
    private TextView tvPrecheckDitchTypeValue;
    private TextView tvPrecheckRefAValue;
    private TextView tvPrecheckRefBValue;
    private TextView tvPrecheckAbDistanceValue;
    private TextView tvPrecheckDepthValue;
    private TextView tvPrecheckLeftWidthValue;
    private TextView tvPrecheckRightWidthValue;
    private TextView tvPrecheckTopWidthValue;
    private View rowPrecheckTopWidth;
    private TextView iconPrecheckRtk;
    private TextView tvPrecheckRtkStatus;
    private TextView tvPrecheckRtkDesc;
    private TextView iconPrecheckImu;
    private TextView tvPrecheckImuStatus;
    private TextView tvPrecheckImuDesc;
    private HelpTooltip helpTooltip;
    private boolean workflowBusy;
    private final DitchTcuWorkflow workflow = DitchTcuWorkflow.getInstance();
    private final RtkState.OnRtkChangeListener rtkChangeListener =
            (lat, lon, valid) -> runOnUiThread(this::refreshPrecheckInfo);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        setContentView(R.layout.activity_ditch_precheck);

        btnBack = findViewById(R.id.btnPrecheckBack);
        btnPrev = findViewById(R.id.btnPrecheckPrev);
        btnStart = findViewById(R.id.btnPrecheckStart);
        tvPrecheckDitchTypeValue = findViewById(R.id.tvPrecheckDitchTypeValue);
        tvPrecheckRefAValue = findViewById(R.id.tvPrecheckRefAValue);
        tvPrecheckRefBValue = findViewById(R.id.tvPrecheckRefBValue);
        tvPrecheckAbDistanceValue = findViewById(R.id.tvPrecheckAbDistanceValue);
        tvPrecheckDepthValue = findViewById(R.id.tvPrecheckDepthValue);
        tvPrecheckLeftWidthValue = findViewById(R.id.tvPrecheckLeftWidthValue);
        tvPrecheckRightWidthValue = findViewById(R.id.tvPrecheckRightWidthValue);
        tvPrecheckTopWidthValue = findViewById(R.id.tvPrecheckTopWidthValue);
        rowPrecheckTopWidth = findViewById(R.id.rowPrecheckTopWidth);
        iconPrecheckRtk = findViewById(R.id.iconPrecheckRtk);
        tvPrecheckRtkStatus = findViewById(R.id.tvPrecheckRtkStatus);
        tvPrecheckRtkDesc = findViewById(R.id.tvPrecheckRtkDesc);
        iconPrecheckImu = findViewById(R.id.iconPrecheckImu);
        tvPrecheckImuStatus = findViewById(R.id.tvPrecheckImuStatus);
        tvPrecheckImuDesc = findViewById(R.id.tvPrecheckImuDesc);

        View help = findViewById(R.id.btnLevelHelp);
        helpTooltip = new HelpTooltip(
                this,
                "这里是帮助提示内容，你可以在此解释挖沟作业前检查项含义。"
        );
        helpTooltip.attach(help);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> exitDitchAndFinish());
        }
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> DitchStepNavigation.goToPrevious(this));
        }
        DitchStepNavigation.bindStepBar(this);
        if (btnStart != null) {
            btnStart.setOnClickListener(v -> confirmAndStart());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        RtkState.addListener(rtkChangeListener);
        refreshPrecheckInfo();
    }

    @Override
    protected void onStop() {
        RtkState.removeListener(rtkChangeListener);
        super.onStop();
        if (helpTooltip != null) helpTooltip.dismiss();
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

    private void confirmAndStart() {
        if (workflowBusy) {
            return;
        }
        if (!DitchTaskState.isTcuParamsAccepted()) {
            Toast.makeText(this, "挖沟参数尚未被 TCU 确认", Toast.LENGTH_SHORT).show();
            return;
        }
        workflowBusy = true;
        setBusy(true);
        workflow.confirmTaskStart(new DitchTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                setBusy(false);
                TaskTypeState.getInstance().setType(TaskTypeState.Type.DITCH);
                WorkRunState.getInstance().setState(WorkRunState.State.RUNNING);
                Toast.makeText(DitchPrecheckActivity.this, "挖沟任务已激活", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(DitchPrecheckActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Toast.makeText(DitchPrecheckActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void exitDitchAndFinish() {
        workflow.cancelPending();
        setBusy(false);
        if (!workflow.isFeatureActive()) {
            DitchTaskState.reset();
            navigateToMain();
            return;
        }
        workflow.exitFeature(new DitchTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                DitchTaskState.reset();
                navigateToMain();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(DitchPrecheckActivity.this, message, Toast.LENGTH_SHORT).show();
                DitchTaskState.reset();
                navigateToMain();
            }
        });
    }

    private void setBusy(boolean busy) {
        workflowBusy = busy;
        if (btnStart != null) {
            btnStart.setEnabled(!busy);
        }
        if (btnPrev != null) {
            btnPrev.setEnabled(!busy);
        }
    }

    private void refreshPrecheckInfo() {
        if (tvPrecheckDitchTypeValue != null) {
            tvPrecheckDitchTypeValue.setText(DitchTaskState.getDitchTypeText());
        }
        if (tvPrecheckRefAValue != null) {
            tvPrecheckRefAValue.setText(DitchTaskState.getRefAText());
        }
        if (tvPrecheckRefBValue != null) {
            tvPrecheckRefBValue.setText(DitchTaskState.getRefBText());
        }
        if (tvPrecheckAbDistanceValue != null) {
            tvPrecheckAbDistanceValue.setText(valueWithMeter(DitchTaskState.getAbDistance()));
        }
        if (tvPrecheckDepthValue != null) {
            tvPrecheckDepthValue.setText(valueWithMeter(DitchTaskState.getSideParam3()));
        }
        if (tvPrecheckLeftWidthValue != null) {
            tvPrecheckLeftWidthValue.setText(valueWithMeter(DitchTaskState.getSideParam1()));
        }
        if (tvPrecheckRightWidthValue != null) {
            tvPrecheckRightWidthValue.setText(valueWithMeter(DitchTaskState.getSideParam4()));
        }
        boolean showTopWidth = !DitchTaskState.isSquareDitch();
        if (rowPrecheckTopWidth != null) {
            rowPrecheckTopWidth.setVisibility(showTopWidth ? View.VISIBLE : View.GONE);
        }
        if (tvPrecheckTopWidthValue != null) {
            tvPrecheckTopWidthValue.setText(valueWithMeter(DitchTaskState.getSideParam2()));
        }
        applyRtkStatus();
        applyImuStatus();
    }

    private void applyRtkStatus() {
        boolean valid = RtkState.isValid();
        int color = valid ? Color.parseColor("#FF22C55E") : Color.parseColor("#FFFF6B6B");
        if (iconPrecheckRtk != null) {
            iconPrecheckRtk.setBackgroundResource(valid ? R.drawable.check_green_bg : R.drawable.check_red_bg);
            iconPrecheckRtk.setText(valid ? "✓" : "!");
        }
        if (tvPrecheckRtkStatus != null) {
            tvPrecheckRtkStatus.setText(valid ? "已连接" : "无数据");
            tvPrecheckRtkStatus.setTextColor(color);
        }
        if (tvPrecheckRtkDesc != null) {
            tvPrecheckRtkDesc.setText(valid
                    ? "当前信号良好，符合作业要求"
                    : "未获取到 RTK 数据，请检查定位状态");
        }
    }

    private void applyImuStatus() {
        int onlineCount = ImuStatusState.getOnlineCount();
        boolean dataOk = ImuStatusState.isImuDataGoodForPrecheckUi();
        int color = dataOk ? Color.parseColor("#FF22C55E") : Color.parseColor("#FFFF6B6B");
        if (iconPrecheckImu != null) {
            iconPrecheckImu.setBackgroundResource(dataOk ? R.drawable.check_green_bg : R.drawable.check_red_bg);
            iconPrecheckImu.setText(dataOk ? "✓" : "!");
        }
        if (tvPrecheckImuStatus != null) {
            tvPrecheckImuStatus.setText(dataOk ? "数据正常" : "IMU " + onlineCount + "/" + ImuStatusState.TOTAL_COUNT);
            tvPrecheckImuStatus.setTextColor(color);
        }
        if (tvPrecheckImuDesc != null) {
            tvPrecheckImuDesc.setText(dataOk
                    ? "IMU 数据已识别，数据正常"
                    : "IMU 数据不完整，请检查传感器状态");
        }
    }

    private static String valueOrPlaceholder(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value.trim();
    }

    private static String valueWithMeter(String value) {
        return valueOrPlaceholder(value) + " m";
    }
}

