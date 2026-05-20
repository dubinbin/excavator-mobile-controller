package com.capstone.excavator;

/**
 * 挖沟 Step3：B 点设置（斗尖 + 高度/坐标定点 + TCU 测 B 点 + AB 距离）。
 */
public class DitchSettingPointBActivity extends DitchPointSettingActivity {

    @Override
    protected boolean isPointA() {
        return false;
    }

    @Override
    protected int getSurveyPointId() {
        return TcuBusinessCodec.POINT_B;
    }

    @Override
    protected int getInitialRef() {
        return DitchTaskState.getRefB();
    }

    @Override
    protected boolean isPointReady() {
        return DitchTaskState.isPointBReady();
    }
}
