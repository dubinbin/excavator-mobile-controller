package com.capstone.excavator;

import androidx.annotation.Nullable;

/**
 * TCU 业务交互帧（{@code 0x55 0xAA}）组包与解析。
 * CRC16-MODBUS 范围：{@code MsgID} … 最后一个 {@code Data} 字节（含 {@code DataLen}）。
 */
public final class TcuBusinessCodec {

    public static final int HEAD_1 = 0x55;
    public static final int HEAD_2 = 0xAA;
    public static final int TAIL = 0xFF;

    public static final int MSG_FEATURE_SELECT = 0x04;
    public static final int MSG_FEATURE_SELECT_ACK = 0x84;
    public static final int MSG_SURVEY_REQUEST = 0x10;
    public static final int MSG_SURVEY_RESULT = 0x90;
    public static final int MSG_LEVEL_PARAMS = 0x11;
    public static final int MSG_LEVEL_PARAMS_ACK = 0x91;
    public static final int MSG_TASK_CONFIRM = 0x40;
    public static final int MSG_TASK_CONFIRM_ACK = 0xC0;
    public static final int MSG_INIT_STATUS = 0x50;
    public static final int MSG_INIT_CONFIRM = 0xD0;

    public static final int FEATURE_NONE = 0x00;
    public static final int FEATURE_LEVEL = 0x01;
    public static final int FEATURE_DITCH = 0x02;
    public static final int FEATURE_SLOPE = 0x03;

    public static final int ACTION_EXIT = 0x00;
    public static final int ACTION_ENTER = 0x01;

    public static final int POINT_REF = 0x00;

    public static final int RESULT_OK = 0x00;

    public static final class ParsedFrame {
        public final int msgId;
        public final byte[] data;
        public final byte[] raw;

        ParsedFrame(int msgId, byte[] data, byte[] raw) {
            this.msgId = msgId;
            this.data = data;
            this.raw = raw;
        }
    }

    private TcuBusinessCodec() {
    }

    /** 组包：{@code head + msgId + data + crc + tail}。 */
    public static byte[] build(int msgId, byte[] data) {
        int dataLen = data == null ? 0 : data.length;
        byte[] crcInput = new byte[1 + dataLen];
        crcInput[0] = (byte) msgId;
        if (dataLen > 0) {
            System.arraycopy(data, 0, crcInput, 1, dataLen);
        }
        int crc = CRC16Modbus.calculateCRC16Modbus(crcInput);
        byte[] frame = new byte[2 + 1 + 1 + dataLen + 2 + 1];
        int i = 0;
        frame[i++] = (byte) HEAD_1;
        frame[i++] = (byte) HEAD_2;
        frame[i++] = (byte) msgId;
        frame[i++] = (byte) dataLen;
        if (dataLen > 0) {
            System.arraycopy(data, 0, frame, i, dataLen);
            i += dataLen;
        }
        byte[] crcBytes = CRC16Modbus.crcToBytes(crc);
        frame[i++] = crcBytes[0];
        frame[i++] = crcBytes[1];
        frame[i] = (byte) TAIL;
        return frame;
    }

    public static byte[] buildFeatureSelect(int featureId, int action) {
        return build(MSG_FEATURE_SELECT, new byte[]{
                (byte) featureId,
                (byte) action
        });
    }

    public static byte[] buildSurveyRequest(int featureId, int pointId, int pointMode) {
        return build(MSG_SURVEY_REQUEST, new byte[]{
                (byte) featureId,
                (byte) pointId,
                (byte) pointMode,
                0x00
        });
    }

    public static byte[] buildLevelParams(int targetHeightTenthCm) {
        byte[] payload = new byte[4];
        writeInt32Be(payload, 0, targetHeightTenthCm);
        return build(MSG_LEVEL_PARAMS, payload);
    }

    public static byte[] buildTaskConfirm(int featureId, int action) {
        return build(MSG_TASK_CONFIRM, new byte[]{
                (byte) featureId,
                (byte) action
        });
    }

    /** §5.1 初始化确认：{@code RetryReason} 单字节。 */
    public static byte[] buildInitConfirm(int retryReason) {
        return build(MSG_INIT_CONFIRM, new byte[]{(byte) retryReason});
    }

    /**
     * 若 {@code raw} 为完整业务帧则解析；CRC 或帧尾错误时返回 {@code null}。
     */
    @Nullable
    public static ParsedFrame tryParse(byte[] raw) {
        if (raw == null || raw.length < 7) {
            return null;
        }
        if ((raw[0] & 0xFF) != HEAD_1 || (raw[1] & 0xFF) != HEAD_2) {
            return null;
        }
        int msgId = raw[2] & 0xFF;
        int dataLen = raw[3] & 0xFF;
        int frameLen = 7 + dataLen;
        if (raw.length < frameLen) {
            return null;
        }
        if ((raw[frameLen - 1] & 0xFF) != TAIL) {
            return null;
        }
        byte[] crcInput = new byte[2 + dataLen];
        System.arraycopy(raw, 2, crcInput, 0, crcInput.length);
        int calc = CRC16Modbus.calculateCRC16Modbus(crcInput);
        int recv = CRC16Modbus.bytesToCRC(raw, 4 + dataLen);
        if (calc != recv) {
            return null;
        }
        byte[] data = new byte[dataLen];
        if (dataLen > 0) {
            System.arraycopy(raw, 4, data, 0, dataLen);
        }
        byte[] copy = new byte[frameLen];
        System.arraycopy(raw, 0, copy, 0, frameLen);
        return new ParsedFrame(msgId, data, copy);
    }

    public static boolean isBusinessFrame(byte[] raw) {
        return raw != null && raw.length >= 2
                && (raw[0] & 0xFF) == HEAD_1
                && (raw[1] & 0xFF) == HEAD_2;
    }

    /** {@code int32} 大端，单位 0.1 cm → 米。 */
    public static double tenthCmToMeters(int tenthCm) {
        return tenthCm / 1000.0;
    }

    /** 米 → 0.1 cm 整数（四舍五入）。 */
    public static int metersToTenthCm(double meters) {
        return (int) Math.round(meters * 1000.0);
    }

    /** {@code int40} 大端，分辨率 1e-9 deg。 */
    public static double parseInt40LatLon(byte[] data, int offset) {
        int b0 = data[offset] & 0xFF;
        boolean negative = (b0 & 0x80) != 0;
        long magnitude = ((long) (b0 & 0x7F) << 32)
                | ((long) (data[offset + 1] & 0xFF) << 24)
                | ((long) (data[offset + 2] & 0xFF) << 16)
                | ((long) (data[offset + 3] & 0xFF) << 8)
                | (long) (data[offset + 4] & 0xFF);
        long signed = negative ? -magnitude : magnitude;
        return signed * 1e-9;
    }

    public static int readInt32Be(byte[] data, int offset) {
        return (data[offset] << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    public static int readUint16Be(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static void writeInt32Be(byte[] dest, int offset, int value) {
        dest[offset] = (byte) (value >> 24);
        dest[offset + 1] = (byte) (value >> 16);
        dest[offset + 2] = (byte) (value >> 8);
        dest[offset + 3] = (byte) value;
    }

    public static String resultMessage(int result) {
        switch (result) {
            case 0x00:
                return "成功";
            case 0x01:
                return "参数非法";
            case 0x02:
                return "当前状态不允许执行";
            case 0x03:
                return "设备未就绪";
            case 0x04:
                return "测点失败";
            case 0x05:
                return "任务上下文缺失";
            case 0x06:
                return "急停状态拒绝执行";
            case 0x07:
                return "忙碌，请重试";
            default:
                return "未知错误(0x" + Integer.toHexString(result) + ")";
        }
    }
}
