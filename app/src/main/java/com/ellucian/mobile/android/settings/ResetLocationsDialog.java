/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;

import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.Utils;

public class ResetLocationsDialog extends DialogPreference {

    private Context context;

    public ResetLocationsDialog(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            PreferencesUtils.removeAllValuesFromPreferences(context, Utils.MUTE_LOCATIONS);

            EllucianApplication application = ((EllucianApplication)context.getApplicationContext());
            application.sendEvent(GoogleAnalyticsConstants.CATEGORY_LOCATIONS, GoogleAnalyticsConstants.ACTION_RESET_MUTE, "User reset their muted beacon notifications", null, "OptionDialogPreference");

            SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = defaultSharedPrefs.edit();
            editor.putString(Utils.BLUETOOTH_NOTIF_REMIND, "0");
            editor.putLong(Utils.BLUETOOTH_NOTIF_TIMESTAMP, 0);
            editor.apply();
        }
    }

}
