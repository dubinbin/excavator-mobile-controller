package com.capstone.excavator;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

final class ExcavatorWebAppBridge {
    interface MessageListener {
        void onWebAppMessage(String message);
    }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    @Nullable
    private static MessageListener listener;

    private ExcavatorWebAppBridge() {
    }

    static void setMessageListener(@Nullable MessageListener nextListener) {
        listener = nextListener;
    }

    static void dispatchMessage(String message) {
        MessageListener current = listener;
        if (current == null) {
            return;
        }
        MAIN_HANDLER.post(() -> {
            MessageListener latest = listener;
            if (latest != null) {
                latest.onWebAppMessage(message);
            }
        });
    }
}
