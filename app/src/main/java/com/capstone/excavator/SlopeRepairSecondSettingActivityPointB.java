package com.capstone.excavator;

public class SlopeRepairSecondSettingActivityPointB extends SlopeRepairPointSettingActivity {

    @Override
    protected boolean isPointA() {
        return false;
    }

    @Override
    protected int getSurveyPointId() {
        return TcuBusinessCodec.POINT_B;
    }
}
