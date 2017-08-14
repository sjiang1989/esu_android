/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.settings;

import android.os.Bundle;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.EllucianActivity;

public class SettingsActivity extends EllucianActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.title_activity_settings);

        getFragmentManager().beginTransaction()
                .replace(R.id.frame_main, new SettingsFragment())
                .commit();
    }

    @Override
    protected void onStart() {
        super.onStart();
        sendView("Settings Page", null);
    }

}
