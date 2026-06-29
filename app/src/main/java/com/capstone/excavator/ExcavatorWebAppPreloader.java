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
    private static final String[] TASK_ROUTES = {
            ExcavatorWebAppView.ROUTE_LEVELING_TASK_STEP1,
            ExcavatorWebAppView.ROUTE_DIG_TASK_STEP1,
            ExcavatorWebAppView.ROUTE_REPAIR_SLOPE_STEP1
    };
    private static final String[] SETTINGS_ROUTES = {
            ExcavatorWebAppView.getSettingsRoute(3),
            ExcavatorWebAppView.getSettingsRoute(0),
            ExcavatorWebAppView.getSettingsRoute(1),
            ExcavatorWebAppView.getSettingsRoute(2)
    };

    @Nullable
    private static ExcavatorWebAppView warmedTaskView;
    @Nullable
    private static ExcavatorWebAppView warmedSettingsView;
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
    static ExcavatorWebAppView takeWarmedTaskView(Activity activity) {
        ExcavatorWebAppView view = warmedTaskView;
        warmedTaskView = null;
        return prepareTakenView(activity, view, "task");
    }

    @Nullable
    static ExcavatorWebAppView takeWarmedSettingsView(Activity activity) {
        ExcavatorWebAppView view = warmedSettingsView;
        warmedSettingsView = null;
        return prepareTakenView(activity, view, "settings");
    }

    @Nullable
    private static ExcavatorWebAppView prepareTakenView(
            Activity activity,
            @Nullable ExcavatorWebAppView view,
            String kind
    ) {
        if (view == null) {
            return null;
        }

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
        Log.i(TAG, "reuse warmed " + kind + " web app view in "
                + activity.getClass().getSimpleName()
                + ", routesReady=" + view.isRouteWarmupComplete());
        return view;
    }

    static boolean hasAllWarmedViews() {
        return warmedTaskView != null && warmedSettingsView != null;
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
            destroyWarmedViews();
        } else if (warmedVersion != null && !assetVersion.equals(warmedVersion)) {
            destroyWarmedViews();
        }

        if (warmedTaskView == null) {
            warmedTaskView = createHiddenView(versionChanged, TASK_ROUTES, "task");
        } else {
            attachHiddenViewIfNeeded(warmedTaskView);
        }
        if (warmedSettingsView == null) {
            warmedSettingsView = createHiddenView(versionChanged, SETTINGS_ROUTES, "settings");
        } else {
            attachHiddenViewIfNeeded(warmedSettingsView);
        }
        warmedVersion = assetVersion;
        prefs.edit().putString(KEY_VERSION, assetVersion).apply();
    }

    private ExcavatorWebAppView createHiddenView(
            boolean bypassInitialCache,
            String[] routes,
            String kind
    ) {
        ExcavatorWebAppView view = new ExcavatorWebAppView(
                UiScaleConfig.unscaledContext(activity),
                bypassInitialCache
        );
        view.setDestroyOnDetach(false);
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.setTranslationX(-10000f);
        view.setClickable(false);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        attachHiddenViewIfNeeded(view);
        view.prewarmRoutes(routes, () ->
                Log.i(TAG, kind + " routes rendered and image-decoded"));
        return view;
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
        destroyWarmedViews();
    }

    private static void destroyWarmedViews() {
        ExcavatorWebAppView taskView = warmedTaskView;
        ExcavatorWebAppView settingsView = warmedSettingsView;
        warmedTaskView = null;
        warmedSettingsView = null;
        warmedVersion = null;
        destroyView(taskView);
        destroyView(settingsView);
    }

    private static void destroyView(@Nullable ExcavatorWebAppView view) {
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
