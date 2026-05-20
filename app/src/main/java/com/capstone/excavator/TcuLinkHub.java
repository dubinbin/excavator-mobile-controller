package com.capstone.excavator;

import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UDP 业务帧发送与分发中枢。{@link MainActivity} 在管道连接成功后注册 {@link Sender}，
 * 收到 {@code 0x55 0xAA} 帧时调用 {@link #dispatch(byte[])}。
 */
public final class TcuLinkHub {

    private static final String TAG = "TcuLinkHub";

    public interface Sender {
        boolean isConnected();

        void write(byte[] data);
    }

    public interface BusinessFrameListener {
        /**
         * @return {@code true} 表示该 listener 已消费此帧，后续 listener 不再收到。
         */
        boolean onBusinessFrame(TcuBusinessCodec.ParsedFrame frame);
    }

    private static volatile Sender sender;
    /** UDP 已收到过有效回包（UDP 无握手时比 {@link Sender#isConnected()} 更可靠）。 */
    private static volatile boolean trafficAlive;
    private static final CopyOnWriteArrayList<BusinessFrameListener> LISTENERS =
            new CopyOnWriteArrayList<>();

    private TcuLinkHub() {
    }

    public static void setSender(@Nullable Sender s) {
        sender = s;
        if (s == null) {
            trafficAlive = false;
        }
    }

    public static void setTrafficAlive(boolean alive) {
        trafficAlive = alive;
    }

    public static boolean isTrafficAlive() {
        return trafficAlive;
    }

    public static boolean isConnected() {
        if (sender == null) {
            return false;
        }
        return trafficAlive || sender.isConnected();
    }

    public static void addListener(BusinessFrameListener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(BusinessFrameListener listener) {
        LISTENERS.remove(listener);
    }

    public static boolean send(byte[] frame) {
        if (sender == null || !sender.isConnected() || frame == null) {
            Log.w(TAG, "send skipped: link not ready");
            return false;
        }
        sender.write(frame);
        return true;
    }

    /**
     * 解析并分发给已注册 listener。若无人消费，返回 {@code false}。
     */
    public static boolean dispatch(byte[] raw) {
        if (!TcuBusinessCodec.isBusinessFrame(raw)) {
            return false;
        }
        TcuBusinessCodec.ParsedFrame frame = TcuBusinessCodec.tryParse(raw);
        if (frame == null) {
            return false;
        }
        for (BusinessFrameListener listener : LISTENERS) {
            try {
                if (listener.onBusinessFrame(frame)) {
                    return true;
                }
            } catch (Throwable t) {
                Log.e(TAG, "listener error", t);
            }
        }
        return false;
    }
}
