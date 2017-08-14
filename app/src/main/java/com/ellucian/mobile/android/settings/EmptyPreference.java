/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.settings;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

// Empty Preference that doesn't do anything. Useful for custom Preferences
public class EmptyPreference extends Preference {

    public EmptyPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

}
