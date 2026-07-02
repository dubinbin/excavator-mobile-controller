package com.capstone.excavator;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TcuHeartbeatCodecTest {

    @Test
    public void buildFrame_roundTripsTimestampAndCrc() {
        long timestamp = 1_719_720_123_456L;

        byte[] frame = TcuHeartbeatCodec.buildFrame(timestamp);

        assertEquals(TcuHeartbeatCodec.FRAME_LENGTH, frame.length);
        assertEquals(Long.valueOf(timestamp), TcuHeartbeatCodec.readTimestamp(frame));
        byte[] payload = Arrays.copyOfRange(frame, 2, 30);
        assertArrayEquals(
                CRC16Modbus.crcToBytes(CRC16Modbus.calculateCRC16Modbus(payload)),
                Arrays.copyOfRange(frame, 30, 32));
        assertEquals((byte) 0xFF, frame[32]);
    }

    @Test
    public void readTimestamp_rejectsNonHeartbeatFrame() {
        assertNull(TcuHeartbeatCodec.readTimestamp(new byte[TcuHeartbeatCodec.FRAME_LENGTH]));
        assertNull(TcuHeartbeatCodec.readTimestamp(new byte[0]));
        assertNull(TcuHeartbeatCodec.readTimestamp(null));
    }
}
