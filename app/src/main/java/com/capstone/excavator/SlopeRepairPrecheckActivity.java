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

public class SlopeRepairPrecheckActivity extends ScaledAppCompatActivity {

    private View btnBack;
    private TextView btnPrev;
    private TextView btnStart;
    private TextView tvPrecheckRepairTypeValue;
    private TextView tvPrecheckRefAValue;
    private TextView tvPrecheckRefBValue;
    private TextView tvPrecheckRefCValue;
    private TextView tvPrecheckAbDistanceValue;
    private TextView tvPrecheckAbLiftValue;
    private TextView tvPrecheckAbHeightDiffValue;
    private TextView tvPrecheckSlopeRatioValue;
    private TextView tvPrecheckVerticalHeightValue;
    private TextView tvPrecheckHorizontalDistanceValue;
    private TextView tvPrecheckSlopeDirectionValue;
    private TextView iconPrecheckRtk;
    private TextView tvPrecheckRtkStatus;
    private TextView tvPrecheckRtkDesc;
    private TextView iconPrecheckImu;
    private TextView tvPrecheckImuStatus;
    private TextView tvPrecheckImuDesc;
    private HelpTooltip helpTooltip;
    private boolean workflowBusy;
    private final SlopeRepairTcuWorkflow workflow = SlopeRepairTcuWorkflow.getInstance();
    private final RtkState.OnRtkChangeListener rtkChangeListener =
            (lat, lon, valid) -> runOnUiThread(this::refreshPrecheckInfo);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        setContentView(R.layout.activity_slope_repair_precheck);

        btnBack = findViewById(R.id.btnPrecheckBack);
        btnPrev = findViewById(R.id.btnPrecheckPrev);
        btnStart = findViewById(R.id.btnPrecheckStart);
        tvPrecheckRepairTypeValue = findViewById(R.id.tvPrecheckRepairTypeValue);
        tvPrecheckRefAValue = findViewById(R.id.tvPrecheckRefAValue);
        tvPrecheckRefBValue = findViewById(R.id.tvPrecheckRefBValue);
        tvPrecheckRefCValue = findViewById(R.id.tvPrecheckRefCValue);
        tvPrecheckAbDistanceValue = findViewById(R.id.tvPrecheckAbDistanceValue);
        tvPrecheckAbLiftValue = findViewById(R.id.tvPrecheckAbLiftValue);
        tvPrecheckAbHeightDiffValue = findViewById(R.id.tvPrecheckAbHeightDiffValue);
        tvPrecheckSlopeRatioValue = findViewById(R.id.tvPrecheckSlopeRatioValue);
        tvPrecheckVerticalHeightValue = findViewById(R.id.tvPrecheckVerticalHeightValue);
        tvPrecheckHorizontalDistanceValue = findViewById(R.id.tvPrecheckHorizontalDistanceValue);
        tvPrecheckSlopeDirectionValue = findViewById(R.id.tvPrecheckSlopeDirectionValue);
        iconPrecheckRtk = findViewById(R.id.iconPrecheckRtk);
        tvPrecheckRtkStatus = findViewById(R.id.tvPrecheckRtkStatus);
        tvPrecheckRtkDesc = findViewById(R.id.tvPrecheckRtkDesc);
        iconPrecheckImu = findViewById(R.id.iconPrecheckImu);
        tvPrecheckImuStatus = findViewById(R.id.tvPrecheckImuStatus);
        tvPrecheckImuDesc = findViewById(R.id.tvPrecheckImuDesc);

        View help = findViewById(R.id.btnLevelHelp);
        helpTooltip = new HelpTooltip(
                this,
                "这里是帮助提示内容，你可以在此解释修坡作业前检查项含义。"
        );
        helpTooltip.attach(help);

        SlopeRepairStepNavigation.bindBackToMain(btnBack, this);
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> SlopeRepairStepNavigation.goToPrevious(this));
        }
        SlopeRepairStepNavigation.bindStepBar(this);
        if (btnStart != null) {
            btnStart.setOnClickListener(v -> confirmTaskStart());
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

    private void refreshPrecheckInfo() {
        setText(tvPrecheckRepairTypeValue, SlopeRepairTaskState.getRepairTypeText());
        setText(tvPrecheckRefAValue, SlopeRepairTaskState.getRefAText());
        setText(tvPrecheckRefBValue, SlopeRepairTaskState.getRefBText());
        setText(tvPrecheckRefCValue, SlopeRepairTaskState.getRefCText());
        setText(tvPrecheckAbDistanceValue, valueWithMeter(SlopeRepairTaskState.getAbDistance()));
        setText(tvPrecheckAbLiftValue, valueWithMeter(SlopeRepairTaskState.getAbLift()));
        setText(tvPrecheckAbHeightDiffValue, valueWithMeter(SlopeRepairTaskState.getAbHeightDiff()));
        setText(tvPrecheckSlopeRatioValue, "1:" + valueOrPlaceholder(SlopeRepairTaskState.getSlopeRatio()));
        setText(tvPrecheckVerticalHeightValue, valueWithMeter(SlopeRepairTaskState.getVerticalHeight()));
        setText(tvPrecheckHorizontalDistanceValue, valueWithMeter(SlopeRepairTaskState.getHorizontalDistance()));
        setText(tvPrecheckSlopeDirectionValue, SlopeRepairTaskState.getSlopeDirectionText());
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

    private void confirmTaskStart() {
        if (workflowBusy) {
            return;
        }
        if (!SlopeRepairTaskState.isTcuParamsAccepted()) {
            Toast.makeText(this, "修坡参数尚未被 TCU 确认", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true);
        workflow.confirmTaskStart(new SlopeRepairTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                setBusy(false);
                TaskTypeState.getInstance().setType(TaskTypeState.Type.SLOPE);
                WorkRunState.getInstance().setState(WorkRunState.State.RUNNING);
                Toast.makeText(SlopeRepairPrecheckActivity.this, "开始作业", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(SlopeRepairPrecheckActivity.this, MainActivity.class));
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Toast.makeText(SlopeRepairPrecheckActivity.this, message, Toast.LENGTH_LONG).show();
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

    private static void setText(TextView textView, String value) {
        if (textView != null) {
            textView.setText(value);
        }
    }

    private static String valueOrPlaceholder(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value.trim();
    }

    private static String valueWithMeter(String value) {
        return valueOrPlaceholder(value) + " m";
    }
}
