package com.capstone.excavator;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

/**
 * 底部覆盖层：左右两侧分立的毛玻璃摇杆指示器，中央是 dock 风格的快捷动作按钮
 * （重连 / 找平 / 挖沟 / 修坡 / 设置）。
 * <p>
 * 当 {@link WorkRunState} 为 {@link WorkRunState.State#RUNNING} 或
 * {@link WorkRunState.State#PAUSED} 时，「重连」与「设置」自动被「暂停」和「结束」替换；
 * 外部可通过 {@link #setOnPauseListener} / {@link #setOnEndListener} 响应点按。
 */
public class BottomBarView extends FrameLayout {

    // ── Event interfaces ─────────────────────────────────────────────

    /** 兼容历史调用：当前布局已没有折叠按钮，触发逻辑由调用方决定。 */
    public interface OnBarToggleListener {
        void onCollapse();
    }

    // ── Internal views ───────────────────────────────────────────────

    private JoystickIndicatorView joystickLeft;
    private JoystickIndicatorView joystickRight;

    private BlurView blurReconnect;
    private BlurView blurWorkGroup;
    private BlurView blurSettings;
    private BlurView blurJoystickRight;
    private BlurView blurJoystickLeft;

    private BlurView blurPause;
    private BlurView blurEnd;

    // ── Callbacks ────────────────────────────────────────────────────

    private Runnable onReconnectListener;
    private Runnable onLevelListener;
    private Runnable onTrenchListener;
    private Runnable onSlopeListener;
    private Runnable onSettingsListener;
    private Runnable onPauseListener;
    private Runnable onEndListener;
    private OnBarToggleListener onBarToggleListener;

    // ── WorkRunState listener ─────────────────────────────────────────

    private final WorkRunState.OnStateChangeListener workStateListener =
            (newState, oldState) -> post(() -> applyWorkState(newState));

    // ── Constructors ─────────────────────────────────────────────────

    public BottomBarView(Context context) {
        this(context, null);
    }

    public BottomBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BottomBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.view_bottom_bar, this);

        joystickLeft  = findViewById(R.id.joystickLeft);
        joystickRight = findViewById(R.id.joystickRight);

        blurReconnect     = findViewById(R.id.btnReconnect);
        blurWorkGroup     = findViewById(R.id.blurWorkGroup);
        blurSettings      = findViewById(R.id.btnSettings);
        blurJoystickRight = findViewById(R.id.blurJoystickRight);
        blurJoystickLeft  = findViewById(R.id.blurJoystickLeft);
        blurPause         = findViewById(R.id.btnPause);
        blurEnd           = findViewById(R.id.btnEnd);

        bindDockButton(R.id.btnReconnect, () -> onReconnectListener);
        bindDockButton(R.id.btnLevel,     () -> onLevelListener);
        bindDockButton(R.id.btnTrench,    () -> onTrenchListener);
        bindDockButton(R.id.btnSlope,     () -> onSlopeListener);
        bindDockButton(R.id.btnSettings,  () -> onSettingsListener);
        bindDockButton(R.id.btnPause,     () -> onPauseListener);
        bindDockButton(R.id.btnEnd,       () -> onEndListener);

        applyWorkState(WorkRunState.getInstance().getState());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WorkRunState.getInstance().addListener(workStateListener);
        post(this::setupDockBlur);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        WorkRunState.getInstance().removeListener(workStateListener);
    }

    // ── Work state UI ─────────────────────────────────────────────────

    private void applyWorkState(WorkRunState.State state) {
        boolean active = state == WorkRunState.State.RUNNING || state == WorkRunState.State.PAUSED;
        setVisible(blurReconnect, !active);
        setVisible(blurSettings,  !active);
        setVisible(blurPause,      active);
        setVisible(blurEnd,        active);
    }

    private static void setVisible(@Nullable View v, boolean visible) {
        if (v != null) v.setVisibility(visible ? VISIBLE : GONE);
    }

    // ── Blur setup ───────────────────────────────────────────────────

    private void setupDockBlur() {
        try {
            View root = getRootView();
            View t = root != null ? root.findViewById(R.id.blurTarget) : null;
            if (!(t instanceof BlurTarget)) return;
            BlurTarget target = (BlurTarget) t;

            final float radius = 18f;
            final int overlay = 0x4D808080;

            setupOneBlur(blurReconnect,     target, radius, overlay);
            setupOneBlur(blurWorkGroup,     target, radius, overlay);
            setupOneBlur(blurSettings,      target, radius, overlay);
            setupOneBlur(blurJoystickRight, target, radius, overlay);
            setupOneBlur(blurJoystickLeft,  target, radius, overlay);
            setupOneBlur(blurPause,         target, radius, overlay);
            setupOneBlur(blurEnd,           target, radius, overlay);
        } catch (Throwable ignored) {
            // Blur 是装饰，失败不应阻塞 UI
        }
    }

    private void setupOneBlur(BlurView blurView, BlurTarget target, float radius, int overlayColor) {
        if (blurView == null) return;
        blurView.setupWith(target)
                .setBlurRadius(radius)
                .setOverlayColor(overlayColor);
        blurView.setClipToOutline(true);
        blurView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    }

    private void bindDockButton(int id, RunnableSupplier supplier) {
        View v = findViewById(id);
        if (v == null) return;
        v.setOnClickListener(view -> {
            Runnable r = supplier.get();
            if (r != null) r.run();
        });
    }

    private interface RunnableSupplier {
        Runnable get();
    }

    // ── Joystick setters ─────────────────────────────────────────────

    /** 更新左摇杆指示位置（x：左右，y：上下，范围 -450~450） */
    public void setJoystickLeft(int x, int y) {
        if (joystickLeft != null) joystickLeft.setValues(x, y);
    }

    /** 更新右摇杆指示位置（x：左右，y：上下，范围 -450~450） */
    public void setJoystickRight(int x, int y) {
        if (joystickRight != null) joystickRight.setValues(x, y);
    }

    // ── Dock action listeners ────────────────────────────────────────

    public void setOnReconnectListener(Runnable listener) { this.onReconnectListener = listener; }
    public void setOnLevelListener(Runnable listener)     { this.onLevelListener     = listener; }
    public void setOnTrenchListener(Runnable listener)    { this.onTrenchListener    = listener; }
    public void setOnSlopeListener(Runnable listener)     { this.onSlopeListener     = listener; }
    public void setOnSettingsListener(Runnable listener)  { this.onSettingsListener  = listener; }

    /** 作业运行中：「暂停」按钮点击回调。 */
    public void setOnPauseListener(Runnable listener)     { this.onPauseListener     = listener; }

    /** 作业运行中：「结束」按钮点击回调。 */
    public void setOnEndListener(Runnable listener)       { this.onEndListener       = listener; }

    public void setOnBarToggleListener(OnBarToggleListener listener) {
        this.onBarToggleListener = listener;
    }

}
