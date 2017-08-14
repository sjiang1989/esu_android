/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * A Utility class with static methods for retrieving and updating values
 * in SharedPreferences
 */
public class PreferencesUtils {

    public static String getStringFromPreferences(Context context, String fileName, String key, String defaultString) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        return preferences.getString(key, defaultString);
    }

    public static void addStringToPreferences(Context context, String fileName, String key, String value) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putString(key, value);
        editor.apply();
    }

    public static Long getLongFromPreferences(Context context, String fileName, String key, Long defaultString) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        return preferences.getLong(key, defaultString);
    }

    public static void addLongToPreferences(Context context, String fileName, String key, Long value) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putLong(key, value);
        editor.apply();
    }

    public static boolean getBooleanFromPreferences(Context context, String fileName, String key, boolean defaultValue) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        return preferences.getBoolean(key, defaultValue);
    }

    public static void addBooleanToPreferences(Context context, String fileName, String key, boolean value) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean(key, value);
        editor.apply();
    }

    public static int getIntFromPreferences(Context context, String fileName, String key, int defaultValue) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        return preferences.getInt(key, defaultValue);
    }

    public static void addIntToPreferences(Context context, String fileName, String key, int value) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putInt(key, value);
        editor.apply();
    }

    public static void removeValuesFromPreferences(Context context, String fileName, String... keys) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        for (String key: keys) {
            editor.remove(key);
        }
        editor.apply();
    }

    public static void removeAllValuesFromPreferences(Context context, String fileName) {
        SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
    }
}
