package com.capstone.excavator;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the packaged excavator SPA alive in a hidden WebView so opening the task page can reuse
 * the already-created renderer and JS runtime instead of only relying on HTTP cache.
 */
final class ExcavatorWebAppPreloader {
    private static final String TAG = "WebAppPreloader";
    private static final String PREFS_NAME = "excavator_web_app_cache";
    private static final String KEY_VERSION = "version";
    private static final String ASSET_INDEX_PATH = "web/excavator-web-app/index.html";
    private static final Pattern VERSION_META_PATTERN = Pattern.compile(
            "<meta\\s+[^>]*name=[\"']version[\"'][^>]*content=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    @Nullable
    private static ExcavatorWebAppView warmedView;
    @Nullable
    private static String warmedVersion;

    private final Activity activity;
    private final String assetVersion;
    private boolean cleanedUp;

    @Nullable
    static ExcavatorWebAppPreloader preload(Activity activity) {
        String version = readAssetVersion(activity);
        if (version == null || version.isEmpty()) {
            Log.w(TAG, "skip preload: version meta not found");
            return null;
        }

        ExcavatorWebAppPreloader preloader = new ExcavatorWebAppPreloader(activity, version);
        preloader.start();
        return preloader;
    }

    @Nullable
    static ExcavatorWebAppView takeWarmedView(Activity activity) {
        ExcavatorWebAppView view = warmedView;
        if (view == null) {
            return null;
        }
        warmedView = null;
        warmedVersion = null;

        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            view.setDestroyOnDetach(false);
            parent.removeView(view);
        }
        view.setAlpha(1f);
        view.setVisibility(View.VISIBLE);
        view.setTranslationX(0f);
        view.setClickable(true);
        view.setFocusable(true);
        view.setDestroyOnDetach(true);
        view.resume();
        Log.i(TAG, "reuse warmed web app view in " + activity.getClass().getSimpleName());
        return view;
    }

    private ExcavatorWebAppPreloader(Activity activity, String assetVersion) {
        this.activity = activity;
        this.assetVersion = assetVersion;
    }

    private void start() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cachedVersion = prefs.getString(KEY_VERSION, null);
        boolean versionChanged = cachedVersion == null || !assetVersion.equals(cachedVersion);

        if (versionChanged) {
            Log.i(TAG, "web app version changed: " + cachedVersion + " -> " + assetVersion);
            destroyWarmedView();
        } else if (warmedView != null && assetVersion.equals(warmedVersion)) {
            attachHiddenViewIfNeeded(warmedView);
            return;
        }

        ExcavatorWebAppView view = new ExcavatorWebAppView(UiScaleConfig.unscaledContext(activity));
        view.setDestroyOnDetach(false);
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.setTranslationX(-10000f);
        view.setClickable(false);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (versionChanged) {
            view.reloadWebEntryIgnoringCache();
        }
        warmedView = view;
        warmedVersion = assetVersion;
        attachHiddenViewIfNeeded(view);
        prefs.edit().putString(KEY_VERSION, assetVersion).apply();
    }

    private void attachHiddenViewIfNeeded(ExcavatorWebAppView view) {
        if (view.getParent() != null) {
            return;
        }
        ViewGroup decorContent = activity.findViewById(android.R.id.content);
        if (decorContent == null) {
            return;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        decorContent.addView(view, lp);
    }

    void cleanup() {
        if (cleanedUp) {
            return;
        }
        cleanedUp = true;
        destroyWarmedView();
    }

    private static void destroyWarmedView() {
        ExcavatorWebAppView view = warmedView;
        warmedView = null;
        warmedVersion = null;
        if (view == null) {
            return;
        }

        ViewGroup parent = (ViewGroup) view.getParent();
        view.setDestroyOnDetach(true);
        if (parent != null) {
            parent.removeView(view);
        } else {
            view.destroyWebView();
        }
    }

    @Nullable
    private static String readAssetVersion(Context context) {
        StringBuilder html = new StringBuilder(4096);
        try (InputStream input = context.getAssets().open(ASSET_INDEX_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(line).append('\n');
            }
        } catch (IOException e) {
            Log.w(TAG, "read index.html failed", e);
            return null;
        }

        Matcher matcher = VERSION_META_PATTERN.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }
}
