package com.capstone.excavator;

/**
 * 挖沟 Step2：A 点设置（斗尖 + 高度/坐标定点 + TCU 测 A 点）。
 */
public class DitchSettingPointAActivity extends DitchPointSettingActivity {

    @Override
    protected boolean isPointA() {
        return true;
    }

    @Override
    protected int getSurveyPointId() {
        return TcuBusinessCodec.POINT_A;
    }

    @Override
    protected int getInitialRef() {
        return DitchTaskState.getRefA();
    }

    @Override
    protected boolean isPointReady() {
        return DitchTaskState.isPointAReady();
    }
}
