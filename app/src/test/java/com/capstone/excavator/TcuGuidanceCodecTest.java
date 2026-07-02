package com.capstone.excavator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TcuGuidanceCodecTest {

    @Test
    public void parsePayload_readsSignedHeightsAndFlags() {
        byte[] payload = new byte[TcuGuidanceCodec.PAYLOAD_LENGTH];
        payload[0] = (byte) (TcuGuidanceCodec.FLAG_CURRENT_HEIGHT_VALID
                | TcuGuidanceCodec.FLAG_TARGET_HEIGHT_VALID
                | TcuGuidanceCodec.FLAG_GUIDANCE_ERROR_VALID
                | TcuGuidanceCodec.FLAG_MODEL_VALID);
        payload[1] = TcuBusinessCodec.FEATURE_DITCH;
        payload[2] = TcuGuidanceCodec.TASK_ACTIVE;
        writeInt32Be(payload, 3, 123_456);
        writeInt32Be(payload, 7, 123_500);
        writeInt32Be(payload, 11, -44);

        TcuGuidanceCodec.Data data = TcuGuidanceCodec.parsePayload(payload);

        assertNotNull(data);
        assertEquals(TcuBusinessCodec.FEATURE_DITCH, data.featureId);
        assertEquals(TcuGuidanceCodec.TASK_ACTIVE, data.taskState);
        assertTrue(data.hasCurrentTipHeight());
        assertTrue(data.hasTargetHeight());
        assertTrue(data.hasGuidanceError());
        assertTrue(data.isModelValid());
        assertEquals(123.456, data.getCurrentTipHeightM(), 0.0001);
        assertEquals(123.500, data.getTargetHeightM(), 0.0001);
        assertEquals(-0.044, data.getGuidanceErrorM(), 0.0001);
    }

    @Test
    public void parsePayload_rejectsShortOrInvalidPayload() {
        assertNull(TcuGuidanceCodec.parsePayload(new byte[14]));

        byte[] payload = new byte[TcuGuidanceCodec.PAYLOAD_LENGTH];
        payload[1] = 0x7F;
        payload[2] = TcuGuidanceCodec.TASK_ACTIVE;
        assertNull(TcuGuidanceCodec.parsePayload(payload));

        payload[1] = TcuBusinessCodec.FEATURE_LEVEL;
        payload[2] = TcuGuidanceCodec.TASK_INACTIVE;
        TcuGuidanceCodec.Data data = TcuGuidanceCodec.parsePayload(payload);
        assertNotNull(data);
        assertFalse(data.hasGuidanceError());
    }

    @Test
    public void standardBusinessFrame_roundTripsGuidancePayload() {
        byte[] payload = new byte[TcuGuidanceCodec.PAYLOAD_LENGTH];
        payload[0] = (byte) (TcuGuidanceCodec.FLAG_GUIDANCE_ERROR_VALID
                | TcuGuidanceCodec.FLAG_MODEL_VALID);
        payload[1] = TcuBusinessCodec.FEATURE_LEVEL;
        payload[2] = TcuGuidanceCodec.TASK_ACTIVE;
        writeInt32Be(payload, 11, 250);
        byte[] frame = buildIncomingFrame(TcuBusinessCodec.MSG_GUIDANCE_REPORT, payload);

        TcuBusinessCodec.ParsedFrame parsedFrame = TcuBusinessCodec.tryParse(frame);
        TcuGuidanceCodec.Data data = TcuGuidanceCodec.parse(parsedFrame);

        assertNotNull(data);
        assertEquals(250, data.guidanceErrorTenthCm);
        assertEquals(0.25, data.getGuidanceErrorM(), 0.0001);
    }

    private static void writeInt32Be(byte[] dest, int offset, int value) {
        dest[offset] = (byte) (value >> 24);
        dest[offset + 1] = (byte) (value >> 16);
        dest[offset + 2] = (byte) (value >> 8);
        dest[offset + 3] = (byte) value;
    }

    private static byte[] buildIncomingFrame(int msgId, byte[] payload) {
        int dataLength = payload.length;
        byte[] frame = new byte[7 + dataLength];
        frame[0] = (byte) TcuBusinessCodec.HEAD_1;
        frame[1] = (byte) TcuBusinessCodec.HEAD_2;
        frame[2] = (byte) msgId;
        frame[3] = (byte) dataLength;
        System.arraycopy(payload, 0, frame, 4, dataLength);
        byte[] crcInput = new byte[2 + dataLength];
        System.arraycopy(frame, 2, crcInput, 0, crcInput.length);
        byte[] crc = CRC16Modbus.crcToBytes(CRC16Modbus.calculateCRC16Modbus(crcInput));
        frame[4 + dataLength] = crc[0];
        frame[5 + dataLength] = crc[1];
        frame[6 + dataLength] = (byte) TcuBusinessCodec.TAIL;
        return frame;
    }
}
