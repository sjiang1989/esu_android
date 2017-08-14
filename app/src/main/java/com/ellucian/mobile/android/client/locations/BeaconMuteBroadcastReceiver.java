/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.locations;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.Utils;

import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.Region;

import java.util.Calendar;

public class BeaconMuteBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String uuid = intent.getStringExtra("uuid");
        String major = intent.getStringExtra("major");
        String minor = intent.getStringExtra("minor");
        String modulesId = intent.getStringExtra("modulesId");
        Boolean tempMute = intent.getBooleanExtra("tempMute", false);

        BeaconNotificationManager beaconNotificationManager = EllucianBeaconManager.getInstance().getBeaconNotificationManager();
        beaconNotificationManager.getNotificationManager().cancel(modulesId, Integer.parseInt(major));

        if (!tempMute) {
            EllucianBeaconManager.getInstance().removeRegionFromBootstrap(new Region(modulesId, Identifier.parse(uuid), Identifier.parse(major), Identifier.parse(minor)));

            EllucianApplication application = ((EllucianApplication) context.getApplicationContext());
            application.sendEvent(GoogleAnalyticsConstants.CATEGORY_LOCATIONS, GoogleAnalyticsConstants.ACTION_MUTE, "User muted beacon notification", null, "BeaconMuteBroadcastReceiver");

            PreferencesUtils.addStringToPreferences(context, Utils.MUTE_LOCATIONS, modulesId, "true");
        } else {
            EllucianApplication application = ((EllucianApplication) context.getApplicationContext());
            application.sendEvent(GoogleAnalyticsConstants.CATEGORY_LOCATIONS, GoogleAnalyticsConstants.ACTION_MUTE, "User temporarily muted beacon notification", null, "BeaconMuteBroadcastReceiver");

            PreferencesUtils.addStringToPreferences(context, Utils.MUTE_LOCATIONS, modulesId, String.valueOf(getStartOfDay()));
        }
    }

    private long getStartOfDay() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

}
