package com.capstone.excavator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class TcuBusinessCodecTest {

    @Test
    public void buildFeatureSelect_crcIncludesMsgIdDataLenAndData() {
        byte[] frame = TcuBusinessCodec.buildFeatureSelect(
                TcuBusinessCodec.FEATURE_LEVEL,
                TcuBusinessCodec.ACTION_ENTER);

        byte[] crcInput = new byte[]{
                (byte) TcuBusinessCodec.MSG_FEATURE_SELECT,
                0x02,
                (byte) TcuBusinessCodec.FEATURE_LEVEL,
                (byte) TcuBusinessCodec.ACTION_ENTER
        };
        byte[] expectedCrc = CRC16Modbus.crcToBytes(
                CRC16Modbus.calculateCRC16Modbus(crcInput));

        assertEquals(9, frame.length);
        assertArrayEquals(expectedCrc, new byte[]{frame[6], frame[7]});
        assertNotNull(TcuBusinessCodec.tryParse(frame));
    }
}
