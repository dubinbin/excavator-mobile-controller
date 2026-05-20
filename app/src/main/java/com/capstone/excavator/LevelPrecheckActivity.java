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

public class LevelPrecheckActivity extends ScaledAppCompatActivity {

    private View btnBack;
    private TextView btnPrev;
    private TextView btnStart;
    private TextView tvPrecheckRef;
    private TextView tvPrecheckMode;
    private TextView tvPrecheckTarget;
    private TextView tvPrecheckFillCut;
    private TextView iconPrecheckRtk;
    private TextView tvPrecheckRtkStatus;
    private TextView tvPrecheckRtkDesc;
    private TextView iconPrecheckImu;
    private TextView tvPrecheckImuStatus;
    private TextView tvPrecheckImuDesc;
    private HelpTooltip helpTooltip;
    private boolean workflowBusy;
    private final LevelTcuWorkflow workflow = LevelTcuWorkflow.getInstance();
    private final RtkState.OnRtkChangeListener rtkChangeListener =
            (lat, lon, valid) -> runOnUiThread(this::refreshPrecheckInfo);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        setContentView(R.layout.activity_level_precheck);

        btnBack = findViewById(R.id.btnPrecheckBack);
        btnPrev = findViewById(R.id.btnPrecheckPrev);
        btnStart = findViewById(R.id.btnPrecheckStart);
        tvPrecheckRef = findViewById(R.id.tvPrecheckRef);
        tvPrecheckMode = findViewById(R.id.tvPrecheckMode);
        tvPrecheckTarget = findViewById(R.id.tvPrecheckTarget);
        tvPrecheckFillCut = findViewById(R.id.tvPrecheckFillCut);
        iconPrecheckRtk = findViewById(R.id.iconPrecheckRtk);
        tvPrecheckRtkStatus = findViewById(R.id.tvPrecheckRtkStatus);
        tvPrecheckRtkDesc = findViewById(R.id.tvPrecheckRtkDesc);
        iconPrecheckImu = findViewById(R.id.iconPrecheckImu);
        tvPrecheckImuStatus = findViewById(R.id.tvPrecheckImuStatus);
        tvPrecheckImuDesc = findViewById(R.id.tvPrecheckImuDesc);

        View help = findViewById(R.id.btnLevelHelp);
        helpTooltip = new HelpTooltip(
                this,
                "确认 TCU 已接受找平参数且传感器正常后，开始作业。"
        );
        helpTooltip.attach(help);

        if (btnBack != null) btnBack.setOnClickListener(v -> exitAndGoMain());
        if (btnPrev != null) btnPrev.setOnClickListener(v -> finish());
        if (btnStart != null) btnStart.setOnClickListener(v -> confirmAndStart());
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
        if (workflow.getPhase().ordinal() < LevelTcuWorkflow.Phase.PARAMS_ACCEPTED.ordinal()) {
            Toast.makeText(this, "找平参数尚未被 TCU 确认", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true);
        workflow.confirmTaskStart(new LevelTcuWorkflow.StepCallback() {
            @Override
            public void onSuccess() {
                setBusy(false);
                TaskTypeState.getInstance().setType(TaskTypeState.Type.LEVEL);
                WorkRunState.getInstance().setState(WorkRunState.State.RUNNING);
                Toast.makeText(LevelPrecheckActivity.this, "找平任务已激活", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LevelPrecheckActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Toast.makeText(LevelPrecheckActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void exitAndGoMain() {
        workflow.cancelPending();
        setBusy(false);
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
                Toast.makeText(LevelPrecheckActivity.this, message, Toast.LENGTH_SHORT).show();
                navigateToMain();
            }
        });
    }

    private void setBusy(boolean busy) {
        workflowBusy = busy;
        if (btnStart != null) btnStart.setEnabled(!busy);
        if (btnPrev != null) btnPrev.setEnabled(!busy);
    }

    private void refreshPrecheckInfo() {
        if (tvPrecheckRef != null) {
            tvPrecheckRef.setText("参考点: " + LevelTaskState.getReferencePointText());
        }
        if (tvPrecheckMode != null) {
            tvPrecheckMode.setText("目标方式: " + LevelTaskState.getModeText());
        }
        if (tvPrecheckTarget != null) {
            tvPrecheckTarget.setText(buildTargetText());
        }
        if (tvPrecheckFillCut != null) {
            tvPrecheckFillCut.setText("填挖量: " + valueOrPlaceholder(LevelTaskState.getFillCut()) + " m");
        }
        applyRtkStatus();
        applyImuStatus();
    }

    private String buildTargetText() {
        if (LevelTaskState.hasAcceptedTargetHeight()) {
            return "TCU 目标高度: " + LevelTaskState.getAcceptedTargetHeightText() + " m";
        }
        if (LevelTaskState.isHeightMode()) {
            return "目标高度: " + valueOrPlaceholder(LevelTaskState.getTargetHeight()) + " m";
        }
        return "目标坐标: 经度 " + valueOrPlaceholder(LevelTaskState.getTargetLon())
                + " / 纬度 " + valueOrPlaceholder(LevelTaskState.getTargetLat())
                + " / 高程 " + valueOrPlaceholder(LevelTaskState.getTargetZ());
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
}
