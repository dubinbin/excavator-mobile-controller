package com.capstone.excavator;

/**
 * Encodes and recognizes the 33-byte echo frame used to measure TCU link RTT.
 */
final class TcuHeartbeatCodec {

    static final int FRAME_LENGTH = 33;

    private static final byte HEADER_1 = (byte) 0xFB;
    private static final byte HEADER_2 = (byte) 0xFB;
    private static final byte TYPE = (byte) 0x01;

    private TcuHeartbeatCodec() {
    }

    static byte[] buildFrame(long timestamp) {
        byte[] frame = new byte[FRAME_LENGTH];
        frame[0] = HEADER_1;
        frame[1] = HEADER_2;
        frame[2] = TYPE;
        for (int i = 0; i < 8; i++) {
            frame[3 + i] = (byte) ((timestamp >> (56 - 8 * i)) & 0xFF);
        }

        byte[] dataForCrc = new byte[28];
        System.arraycopy(frame, 2, dataForCrc, 0, dataForCrc.length);
        byte[] crcBytes = CRC16Modbus.crcToBytes(
                CRC16Modbus.calculateCRC16Modbus(dataForCrc));
        frame[30] = crcBytes[0];
        frame[31] = crcBytes[1];
        frame[32] = (byte) 0xFF;
        return frame;
    }

    static Long readTimestamp(byte[] frame) {
        if (frame == null
                || frame.length != FRAME_LENGTH
                || frame[0] != HEADER_1
                || frame[1] != HEADER_2
                || frame[2] != TYPE) {
            return null;
        }

        long timestamp = 0L;
        for (int i = 0; i < 8; i++) {
            timestamp = (timestamp << 8) | (frame[3 + i] & 0xFF);
        }
        return timestamp;
    }
}
