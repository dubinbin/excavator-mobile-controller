package com.capstone.excavator;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.skydroid.fpvplayer.FPVWidget;
import com.skydroid.fpvplayer.OnPlayerStateListener;
import com.skydroid.fpvplayer.PlayerType;
import com.skydroid.fpvplayer.RtspTransport;

/**
 * Owns the FPV player's configuration, lifecycle, connection UI and persisted stream URL.
 */
final class FpvVideoController {

    private static final String TAG = "FpvVideoController";

    private final Activity activity;
    private final FPVWidget widget;
    private final View placeholder;
    private final View noLiveVideoOverlay;
    private final String defaultUrl;

    private String currentUrl;
    private boolean firstResumeSkipped;

    FpvVideoController(
            Activity activity,
            FPVWidget widget,
            View placeholder,
            View noLiveVideoOverlay,
            String defaultUrl
    ) {
        this.activity = activity;
        this.widget = widget;
        this.placeholder = placeholder;
        this.noLiveVideoOverlay = noLiveVideoOverlay;
        this.defaultUrl = defaultUrl;
        currentUrl = defaultUrl;
    }

    void initialize() {
        setConnected(false);
        if (widget == null) {
            return;
        }

        ControllerLocalSettings.Snapshot snapshot = ControllerLocalSettings.load(activity);
        if (snapshot.videoStreamUrl != null && !snapshot.videoStreamUrl.isEmpty()) {
            currentUrl = snapshot.videoStreamUrl;
        }

        widget.setUsingMediaCodec(true);
        widget.setUrl(currentUrl);
        widget.setPlayerType(PlayerType.ONLY_SKY);
        widget.setRtspTranstype(RtspTransport.AUTO);
        widget.setReConnectInterceptor(() -> false);
        widget.setOnPlayerStateListener(new OnPlayerStateListener() {
            @Override
            public void onConnected() {
                activity.runOnUiThread(() -> setConnected(true));
            }

            @Override
            public void onDisconnect() {
                activity.runOnUiThread(() -> setConnected(false));
            }

            @Override
            public void onReadFrame(com.skydroid.fpvplayer.ffmpeg.FrameInfo frameInfo) {
            }
        });
        widget.start();
    }

    void onResume() {
        if (!firstResumeSkipped) {
            firstResumeSkipped = true;
            return;
        }
        if (widget == null) {
            return;
        }
        try {
            widget.start();
        } catch (Throwable t) {
            Log.w(TAG, "widget.start onResume failed: " + t.getMessage());
        }
    }

    void onPause() {
        if (widget == null) {
            return;
        }
        try {
            widget.stop();
        } catch (Throwable t) {
            Log.w(TAG, "widget.stop onPause failed: " + t.getMessage());
        }
    }

    void reconnect() {
        if (widget == null) {
            return;
        }
        widget.stop();
        widget.setUrl(currentUrl);
        widget.start();
        Toast.makeText(activity, "正在重新连接...", Toast.LENGTH_SHORT).show();
    }

    void updateUrl(String url) {
        if (widget == null) {
            return;
        }
        try {
            widget.stop();
            currentUrl = url;
            widget.setUrl(url);
            ControllerLocalSettings.saveVideoStreamUrl(activity, url);
            widget.start();
            Toast.makeText(activity, "视频地址已更新", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "视频地址更新为: " + url);
        } catch (Exception e) {
            Log.e(TAG, "更新视频地址失败: " + e.getMessage(), e);
            Toast.makeText(activity, "更新失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    String getCurrentUrl() {
        return currentUrl;
    }

    void destroy() {
        if (widget != null) {
            widget.stop();
        }
    }

    private void setConnected(boolean connected) {
        if (placeholder != null) {
            placeholder.setVisibility(connected ? View.GONE : View.VISIBLE);
        }
        if (noLiveVideoOverlay != null) {
            noLiveVideoOverlay.setVisibility(connected ? View.GONE : View.VISIBLE);
        }
    }
}
