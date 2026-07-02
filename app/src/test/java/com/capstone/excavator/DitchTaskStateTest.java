package com.capstone.excavator;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DitchTaskStateTest {

    @Test
    public void coordinatePoints_allowEmptyUnusedHeightModeFields() {
        DitchTaskState.reset();
        DitchTaskState.setHeightMode(false);

        DitchTaskState.updatePointA(
                DitchTaskState.REF_MIDDLE,
                "",
                "",
                "114.169123456",
                "22.312345678",
                "0.873");
        DitchTaskState.updatePointB(
                DitchTaskState.REF_MIDDLE,
                "",
                "",
                "114.169223456",
                "22.312445678",
                "0.873");
        DitchTaskState.updateSideParams("0.45", "0.42", "0.65", "0.25");

        assertTrue(DitchTaskState.canSubmitDitchParams());
    }
}
