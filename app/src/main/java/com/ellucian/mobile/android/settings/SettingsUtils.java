/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * These Preferences use the DEFAULT preferences file and ARE NOT deleted when changing configs.
 *
 * A Utility class with static methods for retrieving and updating values
 * in the apps Default SharedPreferences
 */
public class SettingsUtils {

    public static String getStringFromPreferences(Context context, String key, String defaultString) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(key, defaultString);
    }

    public static void addStringToPreferences(Context context, String key, String value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putString(key, value);
        editor.apply();
    }

    public static Long getLongFromPreferences(Context context, String key, Long defaultString) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getLong(key, defaultString);
    }

    public static void addLongToPreferences(Context context, String key, Long value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putLong(key, value);
        editor.apply();
    }

    public static boolean getBooleanFromPreferences(Context context, String key, boolean defaultValue) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(key, defaultValue);
    }

    public static void addBooleanToPreferences(Context context, String key, boolean value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean(key, value);
        editor.apply();
    }

    public static int getIntFromPreferences(Context context, String key, int defaultValue) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getInt(key, defaultValue);
    }

    public static void addIntToPreferences(Context context, String key, int value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putInt(key, value);
        editor.apply();
    }

    public static void removeValuesFromPreferences(Context context, String... keys) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();

        for (String key: keys) {
            editor.remove(key);
        }
        editor.apply();
    }

    public static void userWasAskedForPermission(Context context, String permission) {
        addBooleanToPreferences(context, permission, true);
    }

    public static boolean hasUserBeenAskedForPermission(Context context, String permission) {
        return getBooleanFromPreferences(context, permission, false);
    }

}
