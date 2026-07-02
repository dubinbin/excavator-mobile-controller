package com.capstone.excavator;

import androidx.annotation.Nullable;

/**
 * TCU 实时引导状态上报（0x52）。
 *
 * <p>Payload：ValidFlags(1) + ActiveFeature(1) + TaskState(1)
 * + CurrentTipHeight(4) + TargetHeight(4) + GuidanceError(4)，共 15 字节。
 * 三个高度字段单位均为 0.1 cm；GuidanceError = CurrentTipHeight - TargetHeight。
 */
public final class TcuGuidanceCodec {

    public static final int PAYLOAD_LENGTH = 15;

    public static final int FLAG_CURRENT_HEIGHT_VALID = 1 << 0;
    public static final int FLAG_TARGET_HEIGHT_VALID = 1 << 1;
    public static final int FLAG_GUIDANCE_ERROR_VALID = 1 << 2;
    public static final int FLAG_MODEL_VALID = 1 << 3;

    public static final int TASK_INACTIVE = 0x00;
    public static final int TASK_ACTIVE = 0x01;
    public static final int TASK_PAUSED = 0x02;

    private TcuGuidanceCodec() {
    }

    @Nullable
    public static Data parse(TcuBusinessCodec.ParsedFrame frame) {
        if (frame == null || frame.msgId != TcuBusinessCodec.MSG_GUIDANCE_REPORT) {
            return null;
        }
        return parsePayload(frame.data);
    }

    @Nullable
    static Data parsePayload(byte[] payload) {
        if (payload == null || payload.length < PAYLOAD_LENGTH) {
            return null;
        }
        int flags = payload[0] & 0xFF;
        int featureId = payload[1] & 0xFF;
        int taskState = payload[2] & 0xFF;
        if (featureId < TcuBusinessCodec.FEATURE_NONE
                || featureId > TcuBusinessCodec.FEATURE_SLOPE
                || taskState < TASK_INACTIVE
                || taskState > TASK_PAUSED) {
            return null;
        }
        return new Data(
                flags,
                featureId,
                taskState,
                TcuBusinessCodec.readInt32Be(payload, 3),
                TcuBusinessCodec.readInt32Be(payload, 7),
                TcuBusinessCodec.readInt32Be(payload, 11));
    }

    public static final class Data {
        public final int validFlags;
        public final int featureId;
        public final int taskState;
        public final int currentTipHeightTenthCm;
        public final int targetHeightTenthCm;
        public final int guidanceErrorTenthCm;

        Data(
                int validFlags,
                int featureId,
                int taskState,
                int currentTipHeightTenthCm,
                int targetHeightTenthCm,
                int guidanceErrorTenthCm) {
            this.validFlags = validFlags;
            this.featureId = featureId;
            this.taskState = taskState;
            this.currentTipHeightTenthCm = currentTipHeightTenthCm;
            this.targetHeightTenthCm = targetHeightTenthCm;
            this.guidanceErrorTenthCm = guidanceErrorTenthCm;
        }

        public boolean hasCurrentTipHeight() {
            return (validFlags & FLAG_CURRENT_HEIGHT_VALID) != 0;
        }

        public boolean hasTargetHeight() {
            return (validFlags & FLAG_TARGET_HEIGHT_VALID) != 0;
        }

        public boolean hasGuidanceError() {
            return (validFlags & FLAG_GUIDANCE_ERROR_VALID) != 0;
        }

        public boolean isModelValid() {
            return (validFlags & FLAG_MODEL_VALID) != 0;
        }

        public double getCurrentTipHeightM() {
            return TcuBusinessCodec.tenthCmToMeters(currentTipHeightTenthCm);
        }

        public double getTargetHeightM() {
            return TcuBusinessCodec.tenthCmToMeters(targetHeightTenthCm);
        }

        public double getGuidanceErrorM() {
            return TcuBusinessCodec.tenthCmToMeters(guidanceErrorTenthCm);
        }
    }
}
