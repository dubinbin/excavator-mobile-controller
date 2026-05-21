package com.capstone.excavator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Header bar component.
 *
 * Props in: setMode(String), setConnected(boolean)（接收机 UDP 链路）, setLinkLatencyMs(int)
 * Self-driven: clock; system battery level via {@link Intent#ACTION_BATTERY_CHANGED}
 *
 * Usage in XML:
 *   <com.capstone.excavator.HeaderBarView
 *       android:id="@+id/headerBar"
 *       android:layout_width="match_parent"
 *       android:layout_height="wrap_content"
 *       android:layout_gravity="top"
 *       android:elevation="10dp" />
 */
public class HeaderBarView extends LinearLayout {

    private TextView tvModeStatus;
    private View pillConnected;
    private TextView tvHeaderConnection;
    private TextView tvCurrentTime;
    private TextView tvBatteryPercent;
    private TextView tvLinkLatency;
    private TextView tvRtkStatus;
    private TextView tvImuStatus;
    private View connectionDot;
    private View rtkStatusDot;
    private View imuStatusDot;
    // private TextView btnEmergencyStop;
    private Runnable onEmergencyStopListener;

    private static final int ONLINE_COLOR = 0xAD059669;
    private static final int OFFLINE_COLOR = 0xFFFF6B6B;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                return;
            }
            applyBatteryIntent(intent);
        }
    };

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvCurrentTime != null) {
                tvCurrentTime.setText(
                        new SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(new Date()));
            }
            clockHandler.postDelayed(this, 1000);
        }
    };

    public HeaderBarView(Context context) {
        this(context, null);
    }

    public HeaderBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HeaderBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setBackgroundColor(0x4D000000);
        inflate(context, R.layout.view_header_bar, this);
        pillConnected      = findViewById(R.id.pillConnected);
        tvHeaderConnection = findViewById(R.id.tvConnectionStatus);
        tvCurrentTime      = findViewById(R.id.tvCurrentTime);
        tvBatteryPercent   = findViewById(R.id.tvBatteryPercent);
        tvLinkLatency      = findViewById(R.id.tvLinkLatency);
        tvRtkStatus        = findViewById(R.id.tvRtkStatus);
        tvImuStatus        = findViewById(R.id.tvImuStatus);
        rtkStatusDot       = findViewById(R.id.rtkStatusDot);
        imuStatusDot       = findViewById(R.id.imuStatusDot);
        // btnEmergencyStop   = findViewById(R.id.btnEmergencyStop);
        // if (btnEmergencyStop != null) {
        //     btnEmergencyStop.setOnClickListener(v -> {
        //         if (onEmergencyStopListener != null) onEmergencyStopListener.run();
        //     });
        // }
        setRtkOnline(false);
        setImuStatus(0, 4, false);
        setLinkLatencyMs(-1);
        setConnected(false);
    }

    /** 外部注册急停按钮点击回调。 */
    public void setOnEmergencyStopListener(Runnable listener) {
        this.onEmergencyStopListener = listener;
    }

    // ── Public API ──────────────────────────────────────────────────

    /** 更新模式文字，例如 "手动模式" / "自动模式" */
    public void setMode(String text) {
        if (tvModeStatus != null) tvModeStatus.setText(text);
    }

    /** 更新接收机链路状态（UDP/TCU 已连通为绿色「已连接」，否则红色「未连接」）。 */
    public void setConnected(boolean connected) {
        int textColor = connected ? ONLINE_COLOR : OFFLINE_COLOR;
        if (tvHeaderConnection != null) {
            tvHeaderConnection.setText(connected ? "已连接" : "未连接");
            tvHeaderConnection.setTextColor(textColor);
        }
        if (pillConnected != null) {
            pillConnected.setBackgroundResource(connected
                    ? R.drawable.connected_pill_bg
                    : R.drawable.disconnected_pill_bg);
        }
        if (connectionDot != null) {
            setDotColor(connectionDot, textColor);
        }
    }

    public void setRtkOnline(boolean online) {
        int color = online ? ONLINE_COLOR : OFFLINE_COLOR;
        if (tvRtkStatus != null) {
            tvRtkStatus.setText("RTK");
            tvRtkStatus.setTextColor(color);
        }
        setDotColor(rtkStatusDot, color);
    }

    /**
     * 链路延迟（毫秒），通常由 {@link MainActivity} 的 UDP 心跳 RTT 更新。
     *
     * @param ms 往返时延（毫秒）；&lt; 0 表示未知或未连接
     */
    public void setLinkLatencyMs(int ms) {
        if (tvLinkLatency == null) {
            return;
        }
        if (ms < 0) {
            tvLinkLatency.setText("—");
        } else {
            tvLinkLatency.setText(ms + " ms");
        }
    }

    /**
     * @param healthyGreen 是否按「TCU 位图 + 角度通道全好」显示绿色；为 {@code false} 时用离线色（灰/红）。
     */
    public void setImuStatus(int onlineCount, int totalCount, boolean healthyGreen) {
        int safeTotal = Math.max(0, totalCount);
        int safeOnline = Math.max(0, Math.min(onlineCount, safeTotal));
        boolean showGreen = healthyGreen && safeOnline == safeTotal && safeTotal > 0;
        int color = showGreen ? ONLINE_COLOR : OFFLINE_COLOR;
        if (tvImuStatus != null) {
            tvImuStatus.setText("IMU " + safeOnline + "/" + safeTotal);
            tvImuStatus.setTextColor(color);
        }
        setDotColor(imuStatusDot, color);
    }

    /** 兼容旧调用：默认仍允许在「全通道在线」时画绿色（未传 TCU 位图时使用）。 */
    public void setImuStatus(int onlineCount, int totalCount) {
        setImuStatus(onlineCount, totalCount, true);
    }

    private void applyBatteryIntent(Intent intent) {
        if (tvBatteryPercent == null || intent == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            int pct = Math.round(100f * level / (float) scale);
            tvBatteryPercent.setText(pct + "%");
        }
    }

    private static void setDotColor(View dotView, int color) {
        if (dotView == null) {
            return;
        }
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        dotView.setBackground(dot);
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        clockHandler.post(clockRunnable);
        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Context ctx = getContext();
        if (ctx != null) {
            Intent sticky;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sticky = ctx.registerReceiver(null, batteryFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                sticky = ctx.registerReceiver(null, batteryFilter);
            }
            if (sticky != null) {
                applyBatteryIntent(sticky);
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ctx.registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    ctx.registerReceiver(batteryReceiver, batteryFilter);
                }
            } catch (Throwable ignored) {
                // 极端环境下注册失败时仍保留占位 UI
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clockHandler.removeCallbacks(clockRunnable);
        Context ctx = getContext();
        if (ctx != null) {
            try {
                ctx.unregisterReceiver(batteryReceiver);
            } catch (Throwable ignored) {
                // 未注册或已注销
            }
        }
    }
}
