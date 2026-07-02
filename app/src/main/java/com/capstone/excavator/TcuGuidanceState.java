package com.capstone.excavator;

import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 最近一帧 TCU 0x52 实时引导数据及其订阅接口。
 */
public final class TcuGuidanceState implements TcuLinkHub.BusinessFrameListener {

    public interface OnUpdateListener {
        void onGuidanceUpdated(@Nullable Snapshot snapshot);
    }

    /** 0x52 期望周期 100 ms，超过此时间不再用于 UI。 */
    public static final long FRESH_TIMEOUT_MS = 500L;

    private static final TcuGuidanceState INSTANCE = new TcuGuidanceState();

    private final CopyOnWriteArrayList<OnUpdateListener> listeners =
            new CopyOnWriteArrayList<>();
    @Nullable
    private volatile Snapshot latest;

    private TcuGuidanceState() {
        TcuLinkHub.addListener(this);
    }

    public static TcuGuidanceState getInstance() {
        return INSTANCE;
    }

    @Nullable
    public Snapshot getLatest() {
        return latest;
    }

    public void addListener(OnUpdateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OnUpdateListener listener) {
        listeners.remove(listener);
    }

    public void clear() {
        latest = null;
        BucketTipHeightState.clear();
        notifyListeners(null);
    }

    @Override
    public boolean onBusinessFrame(TcuBusinessCodec.ParsedFrame frame) {
        if (frame == null || frame.msgId != TcuBusinessCodec.MSG_GUIDANCE_REPORT) {
            return false;
        }
        TcuGuidanceCodec.Data data = TcuGuidanceCodec.parse(frame);
        if (data == null) {
            return true;
        }
        Snapshot snapshot = new Snapshot(data, System.currentTimeMillis());
        latest = snapshot;
        if (data.hasCurrentTipHeight()) {
            BucketTipHeightState.updateTipHeightM(data.getCurrentTipHeightM());
        } else {
            BucketTipHeightState.clear();
        }
        notifyListeners(snapshot);
        return true;
    }

    private void notifyListeners(@Nullable Snapshot snapshot) {
        for (OnUpdateListener listener : listeners) {
            listener.onGuidanceUpdated(snapshot);
        }
    }

    public static final class Snapshot {
        public final TcuGuidanceCodec.Data data;
        public final long receivedAtMs;

        Snapshot(TcuGuidanceCodec.Data data, long receivedAtMs) {
            this.data = data;
            this.receivedAtMs = receivedAtMs;
        }

        public boolean isFresh() {
            long age = System.currentTimeMillis() - receivedAtMs;
            return age >= 0L && age <= FRESH_TIMEOUT_MS;
        }
    }
}
