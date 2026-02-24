package com.example.myapplication;

/**
 * CRC-16-MODBUS校验工具类
 * 用于计算数据的CRC-16-MODBUS校验值
 */
public class CRC16Modbus {
    
    /**
     * 计算CRC-16-MODBUS校验值
     * @param data 需要计算CRC的数据字节数组
     * @return CRC-16校验值（2字节，低字节在前）
     */
    public static int calculateCRC16Modbus(byte[] data) {
        int crc = 0xFFFF;
        
        for (byte b : data) {
            crc = crc ^ (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc = crc >> 1;
                }
            }
        }
        
        return crc;
    }
    
    /**
     * 将CRC值转换为字节数组（低字节在前）
     * @param crc CRC校验值
     * @return 2字节数组，[低字节, 高字节]
     */
    public static byte[] crcToBytes(int crc) {
        return new byte[]{
            (byte) (crc & 0xFF),           // 低字节
            (byte) ((crc >> 8) & 0xFF)     // 高字节
        };
    }
    
    /**
     * 从字节数组中读取CRC值（低字节在前）
     * @param data 包含CRC的字节数组
     * @param offset CRC在数组中的偏移量
     * @return CRC校验值
     */
    public static int bytesToCRC(byte[] data, int offset) {
        int lowByte = data[offset] & 0xFF;
        int highByte = data[offset + 1] & 0xFF;
        return (highByte << 8) | lowByte;
    }
}

