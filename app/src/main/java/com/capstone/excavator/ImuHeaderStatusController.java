package com.capstone.excavator;

/**
 * Converts parsed IMU/RTK and TCU link state into the compact status shown by the header.
 */
final class ImuHeaderStatusController {

    private HeaderBarView headerBar;
    private IMUDataParser.ParsedData lastParsedSnapshot;

    void bind(HeaderBarView headerBar) {
        this.headerBar = headerBar;
    }

    void setOffline() {
        RtkState.clear();
        ImuStatusState.clear();
        ImuRealtimeState.clear();
        BucketTipHeightState.clear();
        lastParsedSnapshot = null;
        if (headerBar != null) {
            headerBar.setRtkOnline(false);
            headerBar.setImuStatus(
                    ImuStatusState.getOnlineCount(),
                    ImuStatusState.TOTAL_COUNT,
                    false);
        }
    }

    void update(IMUDataParser.ParsedData parsed) {
        if (parsed == null) {
            return;
        }
        lastParsedSnapshot = parsed;
        ImuStatusState.setOnlineCount(countOnlineImus(parsed));
        if (headerBar != null) {
            headerBar.setImuStatus(
                    ImuStatusState.getOnlineCount(),
                    ImuStatusState.TOTAL_COUNT,
                    showsHealthyGreen(parsed));
            headerBar.setRtkOnline(RtkState.isValidCoordinate(parsed.rtkLat, parsed.rtkLon));
        }
    }

    void refreshFromTcuLinkState() {
        if (headerBar == null) {
            return;
        }
        IMUDataParser.ParsedData parsed = lastParsedSnapshot;
        boolean healthy = parsed != null && showsHealthyGreen(parsed);
        headerBar.setImuStatus(
                ImuStatusState.getOnlineCount(),
                ImuStatusState.TOTAL_COUNT,
                healthy);
        if (parsed != null) {
            headerBar.setRtkOnline(RtkState.isValidCoordinate(parsed.rtkLat, parsed.rtkLon));
        }
    }

    /**
     * Green requires all angle channels. A known TCU bitmap denial wins; when the bitmap is
     * unknown, five zero angles are treated as an empty placeholder frame.
     */
    private boolean showsHealthyGreen(IMUDataParser.ParsedData parsed) {
        if (countOnlineImus(parsed) != ImuStatusState.TOTAL_COUNT) {
            return false;
        }
        if (ImuStatusState.tcuDeniesImuHealthyGreen()) {
            return false;
        }
        if (ImuStatusState.tcuAssertsImuHealthyGreen()) {
            return true;
        }
        return !allAnglesEffectivelyZero(parsed);
    }

    private static boolean allAnglesEffectivelyZero(IMUDataParser.ParsedData parsed) {
        final float epsilon = 1e-4f;
        return Math.abs(parsed.boomAngle) < epsilon
                && Math.abs(parsed.stickAngle) < epsilon
                && Math.abs(parsed.bucketAngle) < epsilon
                && Math.abs(parsed.cabinPitchAngle) < epsilon
                && Math.abs(parsed.cabinRollAngle) < epsilon;
    }

    private static int countOnlineImus(IMUDataParser.ParsedData parsed) {
        int online = 0;
        if (isFinite(parsed.boomAngle)) online++;
        if (isFinite(parsed.stickAngle)) online++;
        if (isFinite(parsed.bucketAngle)) online++;
        if (isFinite(parsed.cabinPitchAngle) && isFinite(parsed.cabinRollAngle)) online++;
        return online;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
