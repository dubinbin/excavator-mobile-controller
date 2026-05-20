package com.capstone.excavator;

import android.util.Log;

import androidx.annotation.Nullable;

/**
 * TCU 上电初始化握手（imu.txt §5.1 / §6.1）：TCU 发 {@code 0x50}，App 回 {@code 0xD0}。
 * 收到 {@code RetryReason=0x00} 或 {@code 0x02} 后 TCU 才进入主程序并发送 {@code 0xFA} 实时流。
 */
public final class TcuInitHandshake {

    private static final String TAG = "TcuInitHandshake";

    /** 初始化状态上报（TCU → App） */
    public static final int MSG_INIT_STATUS = 0x50;
    /** 初始化确认（App → TCU） */
    public static final int MSG_INIT_CONFIRM = 0xD0;

    public static final int RETRY_REASON_OK = 0x00;
    public static final int RETRY_REASON_RETRY = 0x01;
    public static final int RETRY_REASON_IGNORE = 0x02;

    /** InitBitmap bit0~bit8 均应为 1 表示初始化正常（见协议 §5.1）。 */
    private static final int INIT_BITMAP_MASK = 0x01FF;

    public static final class InitStatus {
        public final int initBitmap;
        public final int protoMajor;
        public final int protoMinor;
        public final int supportBitmap;

        InitStatus(int initBitmap, int protoMajor, int protoMinor, int supportBitmap) {
            this.initBitmap = initBitmap;
            this.protoMajor = protoMajor;
            this.protoMinor = protoMinor;
            this.supportBitmap = supportBitmap;
        }

        public boolean isAllBitsOk() {
            return (initBitmap & INIT_BITMAP_MASK) == INIT_BITMAP_MASK;
        }
    }

    private static volatile boolean mainProgramEntered;

    private TcuInitHandshake() {
    }

    public static boolean isMainProgramEntered() {
        return mainProgramEntered;
    }

    public static void reset() {
        mainProgramEntered = false;
    }

    /**
     * 若 {@code raw} 为 {@code 0x50} 初始化状态帧，则解析、更新 IMU 位图并发送 {@code 0xD0}。
     *
     * @return {@code true} 表示已消费该 UDP 包
     */
    public static boolean tryHandle(byte[] raw) {
        TcuBusinessCodec.ParsedFrame frame = TcuBusinessCodec.tryParse(raw);
        if (frame == null || frame.msgId != MSG_INIT_STATUS) {
            return false;
        }
        InitStatus status = parseInitStatus(frame.data);
        if (status == null) {
            Log.w(TAG, "0x50 数据区长度不足");
            return true;
        }

        ImuStatusState.setTcuImuLinkFromBitmap(status.initBitmap);

        int retryReason = chooseRetryReason(status);
        boolean sent = sendInitConfirm(retryReason);
        if (sent && (retryReason == RETRY_REASON_OK || retryReason == RETRY_REASON_IGNORE)) {
            mainProgramEntered = true;
        }

        Log.i(TAG, String.format(
                "0x50 InitBitmap=0x%04X proto=%d.%d support=0x%04X -> 0xD0 RetryReason=0x%02X sent=%s",
                status.initBitmap,
                status.protoMajor,
                status.protoMinor,
                status.supportBitmap,
                retryReason,
                sent));
        return true;
    }

    /** 用户选择「重试初始化」时调用。 */
    public static boolean sendRetryInit() {
        mainProgramEntered = false;
        return sendInitConfirm(RETRY_REASON_RETRY);
    }

    /** 用户选择「忽略异常继续」时调用。 */
    public static boolean sendIgnoreAndContinue() {
        boolean sent = sendInitConfirm(RETRY_REASON_IGNORE);
        if (sent) {
            mainProgramEntered = true;
        }
        return sent;
    }

    @Nullable
    private static InitStatus parseInitStatus(byte[] data) {
        if (data == null || data.length < 2) {
            return null;
        }
        int initBitmap = TcuBusinessCodec.readUint16Be(data, 0);
        int protoMajor = data.length > 2 ? (data[2] & 0xFF) : 0;
        int protoMinor = data.length > 3 ? (data[3] & 0xFF) : 0;
        int supportBitmap = data.length >= 6 ? TcuBusinessCodec.readUint16Be(data, 4) : 0;
        return new InitStatus(initBitmap, protoMajor, protoMinor, supportBitmap);
    }

    private static int chooseRetryReason(InitStatus status) {
        if (status.isAllBitsOk()) {
            return RETRY_REASON_OK;
        }
        // 无初始化异常 UI 时：带异常位图仍发 0x02，便于联调/mock 进入主程序并收到 0xFA 流
        Log.w(TAG, "InitBitmap 非全 1，使用 RetryReason=0x02 忽略并继续: 0x"
                + Integer.toHexString(status.initBitmap));
        return RETRY_REASON_IGNORE;
    }

    public static boolean sendInitConfirm(int retryReason) {
        byte[] frame = TcuBusinessCodec.buildInitConfirm(retryReason);
        if (!TcuLinkHub.send(frame)) {
            Log.w(TAG, "0xD0 发送失败，请检查 UDP 链路");
            return false;
        }
        return true;
    }
}
