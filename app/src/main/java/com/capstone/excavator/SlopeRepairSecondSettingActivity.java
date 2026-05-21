package com.capstone.excavator;

public class SlopeRepairSecondSettingActivity extends SlopeRepairPointSettingActivity {

    @Override
    protected boolean isPointA() {
        return true;
    }

    @Override
    protected int getSurveyPointId() {
        return TcuBusinessCodec.POINT_A;
    }
}
