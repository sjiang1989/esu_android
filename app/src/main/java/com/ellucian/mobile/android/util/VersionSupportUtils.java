/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.util;

import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.webkit.WebSettings;
import android.widget.TextView;

/**
 * A Utility class with static methods for supporting multiple versions of
 * Android. Methods can be easily updated when the minimum API version is changed.
 */
public class VersionSupportUtils {
    public static final String TAG = VersionSupportUtils.class.getSimpleName();


    @SuppressWarnings("deprecation")
    public static int getColorHelper(Context context, int colorId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.getResources().getColor(colorId, null);
        } else {
            return context.getResources().getColor(colorId);
        }
    }

    @SuppressWarnings("deprecation")
    public static int getColorHelper(View view, int colorId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return view.getResources().getColor(colorId, null);
        } else {
            return view.getResources().getColor(colorId);
        }
    }

    @SuppressWarnings("deprecation")
    public static Drawable getDrawableHelper(Context context, int drawableId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return context.getResources().getDrawable(drawableId, null);
        } else {
            return context.getResources().getDrawable(drawableId);
        }
    }

    @SuppressWarnings("deprecation")
    public static void setDatabasePath(WebSettings webSettings, String databasePath) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // deprecated, but needed for earlier than API 19
            webSettings.setDatabasePath(databasePath);
        }
    }

    public static void enableMirroredDrawable(Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            drawable.setAutoMirrored(true);
        }
    }

    @SuppressWarnings("deprecation")
    public static void setTextAppearanceHelper(Context context, TextView view, int resId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setTextAppearance(resId);
        } else {
            view.setTextAppearance(context, resId);
        }
    }

    @SuppressWarnings("deprecation")
    public static Spanned fromHtml(String htmlString) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(htmlString, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(htmlString);
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean doesDeviceHaveScreenLockOn(Context context, KeyguardManager keyguardManager) {
        if (keyguardManager.isKeyguardSecure()) {
            // password or pin has been enabled
            return true;
        } else {
            ContentResolver cr = context.getContentResolver();
            try {
                int lockPatternEnable = Settings.Secure.getInt(cr, Settings.Secure.LOCK_PATTERN_ENABLED);
                return lockPatternEnable == 1;
            } catch (Settings.SettingNotFoundException e) {
                return false;
            }
        }
    }

}
