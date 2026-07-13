package com.capstone.excavator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局作业运行状态单例。
 * <p>
 * 状态枚举：{@link State#IDLE}（未开始） → {@link State#RUNNING}（运行中） →
 * {@link State#PAUSED}（停止/暂停） → {@link State#ENDED}（已结束）。
 * <p>
 * 通过 {@link #getInstance()} 获取单例，调用 {@link #setState(State)} 修改状态；
 * 任何对象可通过 {@link #addListener(OnStateChangeListener)} 监听变化。
 */
public final class WorkRunState {

    /** 作业运行状态。 */
    public enum State {
        /** 未开始（默认）。 */
        IDLE,
        /** 运行中。 */
        RUNNING,
        /** 暂停/停止。 */
        PAUSED,
        /** 已结束。 */
        ENDED
    }

    public interface OnStateChangeListener {
        void onStateChanged(@NonNull State newState, @NonNull State oldState);
    }

    private static final WorkRunState INSTANCE = new WorkRunState();

    private volatile State current = State.IDLE;
    private final CopyOnWriteArrayList<OnStateChangeListener> listeners = new CopyOnWriteArrayList<>();

    private WorkRunState() {
    }

    public static WorkRunState getInstance() {
        return INSTANCE;
    }

    /** 获取当前状态。 */
    @NonNull
    public State getState() {
        return current;
    }

    /**
     * 设置新状态；若与当前相同则无操作，不发回调。
     */
    public void setState(@NonNull State newState) {
        State old = current;
        if (old == newState) {
            return;
        }
        current = newState;
        for (OnStateChangeListener l : listeners) {
            l.onStateChanged(newState, old);
        }
    }


    public void addListener(@NonNull OnStateChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@Nullable OnStateChangeListener listener) {
        listeners.remove(listener);
    }

    /** 便捷判断：当前是否处于「运行中或暂停」（需要显示暂停/结束按钮的时机）。 */
    public boolean isActiveOrPaused() {
        return current == State.RUNNING || current == State.PAUSED;
    }
}
