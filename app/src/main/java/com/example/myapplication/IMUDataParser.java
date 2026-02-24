package com.example.myapplication;

import android.util.Log;

/**
 * IMU数据解析类
 * 解析TCU发送的14字节IMU数据包
 * 数据格式：2字节帧头(0xFA 0xFA) + 9字节数据(3个角度各3字节) + 2字节CRC + 1字节帧尾(0xFF)
 */
public class IMUDataParser {
    
    private static final String TAG = "IMUDataParser";
    
    // 数据包常量
    private static final int PACKET_SIZE = 14;
    private static final byte FRAME_HEADER_1 = (byte) 0xFA;
    private static final byte FRAME_HEADER_2 = (byte) 0xFA;
    private static final byte FRAME_TAIL = (byte) 0xFF;
    
    // 数据位置常量
    private static final int HEADER_START = 0;
    private static final int DATA_START = 2;        // 字节3开始（索引2）
    private static final int DATA_LENGTH = 9;       // 9字节数据
    private static final int CRC_START = 11;       // 字节12开始（索引11）
    private static final int TAIL_POS = 13;        // 字节14（索引13）
    
    // 角度数据位置
    private static final int BOOM_START = 2;       // 字节3-5（索引2-4）
    private static final int STICK_START = 5;      // 字节6-8（索引5-7）
    private static final int BUCKET_START = 8;     // 字节9-11（索引8-10）
    
    /**
     * 解析结果回调接口
     */
    public interface ParseResultCallback {
        void onParseSuccess(float boomAngle, float stickAngle, float bucketAngle);
        void onParseError(String error);
    }
    
    /**
     * 解析14字节IMU数据包
     * @param data 接收到的数据包（14字节）
     * @param callback 解析结果回调
     * @return 是否解析成功
     */
    public static boolean parseData(byte[] data, ParseResultCallback callback) {
        if (data == null || data.length != PACKET_SIZE) {
            if (callback != null) {
                callback.onParseError("数据包长度错误，期望14字节，实际: " + 
                    (data == null ? "null" : data.length));
            }
            Log.e(TAG, "数据包长度错误: " + (data == null ? "null" : data.length));
            return false;
        }
        
        // 1. 验证帧头
        if (data[HEADER_START] != FRAME_HEADER_1 || data[HEADER_START + 1] != FRAME_HEADER_2) {
            String error = String.format("帧头错误: 0x%02X 0x%02X", 
                data[HEADER_START] & 0xFF, data[HEADER_START + 1] & 0xFF);
            if (callback != null) {
                callback.onParseError(error);
            }
            Log.e(TAG, error);
            return false;
        }
        
        // 2. 验证帧尾
        if (data[TAIL_POS] != FRAME_TAIL) {
            String error = String.format("帧尾错误: 0x%02X", data[TAIL_POS] & 0xFF);
            if (callback != null) {
                callback.onParseError(error);
            }
            Log.e(TAG, error);
            return false;
        }
        
        // 3. CRC校验（对字节3-11，即索引2-10的9字节数据进行校验）
        byte[] dataForCrc = new byte[DATA_LENGTH];
        System.arraycopy(data, DATA_START, dataForCrc, 0, DATA_LENGTH);
        int calculatedCrc = CRC16Modbus.calculateCRC16Modbus(dataForCrc);
        int receivedCrc = CRC16Modbus.bytesToCRC(data, CRC_START);
        
        if (calculatedCrc != receivedCrc) {
            String error = String.format("CRC校验失败: 计算值=0x%04X, 接收值=0x%04X", 
                calculatedCrc, receivedCrc);
            if (callback != null) {
                callback.onParseError(error);
            }
            Log.e(TAG, error);
            return false;
        }
        
        
        // 4. 解析角度数据
        try {
            float boomAngle = parseBCDAngle(data, BOOM_START);
            float stickAngle = parseBCDAngle(data, STICK_START);
            float bucketAngle = parseBCDAngle(data, BUCKET_START);
            
            Log.d(TAG, String.format("解析成功 - 大臂: %.2f°, 小臂: %.2f°, 铲斗: %.2f°", 
                boomAngle, stickAngle, bucketAngle));
            
            if (callback != null) {
                callback.onParseSuccess(boomAngle, stickAngle, bucketAngle);
            }
            return true;
            
        } catch (Exception e) {
            String error = "角度解析失败: " + e.getMessage();
            if (callback != null) {
                callback.onParseError(error);
            }
            Log.e(TAG, error, e);
            return false;
        }
    }
    
    /**
     * 解析BCD编码的角度值
     * 格式：3字节 BCD码
     * - 字节1: 高4位（bit 7-4）为符号位（0000=正，0001=负），低4位（bit 3-0）为整数高位（0-9）
     * - 字节2: XX（整数部分，BCD码，0-99）
     * - 字节3: xx（小数部分，BCD码，0-99）
     * 
     * @param data 数据包
     * @param offset 角度数据在数据包中的起始位置
     * @return 解析后的角度值（浮点数）
     */
    private static float parseBCDAngle(byte[] data, int offset) {
        // 字节1: 高4位符号位 + 低4位数字
        byte byte1 = data[offset];
        
        // 先转换为无符号整数（0-255），确保位运算正确
        int byte1Unsigned = byte1 & 0xFF;
        
        // 提取高4位（bit 7-4）用于符号判断
        // 0000 = 正数，0001 = 负数
        int high4Bits = (byte1Unsigned >> 4) & 0x0F;
        boolean isNegative = (high4Bits == 0x01);  // 高4位为0001表示负数
        
        // 提取低4位（bit 3-0）作为整数高位（0-9）
        int integerHigh = byte1Unsigned & 0x0F;
        
        // 字节2: XX（BCD码，例如0x35表示35，范围0-99）
        byte byte2 = data[offset + 1];
        int integerLow = ((byte2 >> 4) & 0x0F) * 10 + (byte2 & 0x0F);
        
        // 字节3: xx（BCD码，例如0x36表示36，范围0-99）
        byte byte3 = data[offset + 2];
        int decimal = ((byte3 >> 4) & 0x0F) * 10 + (byte3 & 0x0F);
        
        // 组合整数部分：X * 100 + XX
        // integerHigh范围0-9，所以最大是9*100+99=999度
        int integerPart = integerHigh * 100 + integerLow;
        
        // 组合小数部分：xx / 100.0
        float decimalPart = decimal / 100.0f;
        
        // 最终角度值
        float angle = integerPart + decimalPart;
        
        // 应用符号：高4位0000=正，0001=负
        return isNegative ? -angle : angle;
    }
}

