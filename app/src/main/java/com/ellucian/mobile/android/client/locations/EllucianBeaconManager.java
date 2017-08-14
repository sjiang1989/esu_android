/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.locations;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.provider.EllucianContract;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.Utils;

import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

public class EllucianBeaconManager implements BootstrapNotifier {
    private static final String TAG = EllucianBeaconManager.class.getSimpleName();

    private static final EllucianBeaconManager INSTANCE = new EllucianBeaconManager();
    private EllucianBeaconManager() {}

    private Dictionary<String, Long> beaconExitTimes;
    private static final long FIVE_MIN_IN_MILLISECS = 300000;

    // Necessary for background beacon monitoring
    private RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private BeaconNotificationManager beaconNotificationManager;
    private org.altbeacon.beacon.BeaconManager beaconManager;
    private List<Region> currRegions;
    private EllucianApplication application;

    public static EllucianBeaconManager getInstance() {
        return INSTANCE;
    }

    // Not a constructor, but required before anything else can be done
    public void initializer(EllucianApplication application) {
        this.application = application;

        if (currRegions == null) {
            Log.v(TAG, "currRegions should only be null once");
            currRegions = new ArrayList<>();
        }

        beaconExitTimes = new Hashtable<>();

        beaconNotificationManager = new BeaconNotificationManager(application);
        startBeaconManager();
    }

    public void startBeaconManager() {
        beaconManager = org.altbeacon.beacon.BeaconManager.getInstanceForApplication(application);

        beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        List<Region> regions = getBeaconRegions();
        if (regions.size() > 0) {
            regionBootstrap = new RegionBootstrap(this, getBeaconRegions());
        } else if (currRegions.size() > 0) {
            for (int j = 0; j < currRegions.size(); j++) {
                removeRegionFromBootstrap(currRegions.get(j));
            }
        }

        currRegions = new ArrayList<>();
        for (int i = 0; i < regions.size(); i++) {
            currRegions.add(regions.get(i));
        }

        // Reduces bluetooth power usage by about 60%
        backgroundPowerSaver = new BackgroundPowerSaver(application);
    }

    private List<Region> getBeaconRegions() {
        Log.v(TAG, "getting beacon regions");
        List<Region> regions = new ArrayList<>();

        // Only fill the regions list if there is a module that can use beacons to launch
        if (application.configUsesBeacons()) {
            final ContentResolver contentResolver = application.getContentResolver();

            Cursor beaconsCursor = contentResolver.query(EllucianContract.ModulesBeacons.CONTENT_URI,
                    new String[]{EllucianContract.ModulesBeacons.MODULES_BEACONS_UUID,
                            EllucianContract.ModulesBeacons.MODULES_BEACONS_MAJOR,
                            EllucianContract.ModulesBeacons.MODULES_BEACONS_MINOR,
                            EllucianContract.Modules.MODULES_ID},
                    null, null,
                    EllucianContract.ModulesBeacons.DEFAULT_SORT);

            if (beaconsCursor != null && beaconsCursor.moveToFirst()) {
                do {
                    int uuidIndex = beaconsCursor.getColumnIndex(EllucianContract.ModulesBeacons.MODULES_BEACONS_UUID);
                    int majorIndex = beaconsCursor.getColumnIndex(EllucianContract.ModulesBeacons.MODULES_BEACONS_MAJOR);
                    int minorIndex = beaconsCursor.getColumnIndex(EllucianContract.ModulesBeacons.MODULES_BEACONS_MINOR);
                    int idIndex = beaconsCursor.getColumnIndex(EllucianContract.Modules.MODULES_ID);

                    String uuid = beaconsCursor.getString(uuidIndex);
                    String major = beaconsCursor.getString(majorIndex);
                    String minor = beaconsCursor.getString(minorIndex);
                    String id = beaconsCursor.getString(idIndex);

                    if (isUserInterestedInBeacons(id)) {
                        Region newRegion = new Region(id, Identifier.parse(uuid), Identifier.parse(major), Identifier.parse(minor));
                        if (!regions.contains(newRegion)) {
                            regions.add(newRegion);
                            Log.v(TAG, "Beacon added to region: uuid-->" + uuid + " major-->" + major + " minor-->" + minor);
                        }
                    }
                } while (beaconsCursor.moveToNext());
                beaconsCursor.close();
            }
        }
        return regions;
    }

    private boolean isUserInterestedInBeacons(String moduleId) {
        return PreferencesUtils.getStringFromPreferences(application, Utils.MUTE_LOCATIONS, moduleId, "false").equals("false");
    }

    private String getDictionaryKey(Region region) {
        return region.getId1().toString() + region.getId2().toString() + region.getId3().toString();
    }

    public void removeRegionFromBootstrap(Region region) {
        try {
            beaconManager.stopMonitoringBeaconsInRegion(region);
            beaconExitTimes.remove(getDictionaryKey(region));
            Log.v(TAG, "Stopped monitoring a BeaconManager region.");
        } catch (Exception e) { Log.e(TAG, "Failed to stop BeaconManager from monitoring region"); }
    }

    @Override
    public Context getApplicationContext() {
        return application;
    }

    @Override
    public void didEnterRegion(Region region) {
        Log.d(TAG, "Beacon entered region: uuid-->" + region.getId1() + " major-->" + region.getId2() + " minor-->" + region.getId3());
        if (beaconExitTimes.get(getDictionaryKey(region)) == null) {
            beaconNotificationManager.sendNotification(region.getUniqueId(), region.getId1().toString(),
                    region.getId2().toString(), region.getId3().toString(), application.isUserAuthenticated(), application.getAppUserRoles());
        } else if (System.currentTimeMillis() - beaconExitTimes.get(getDictionaryKey(region)) >= FIVE_MIN_IN_MILLISECS) {
            beaconNotificationManager.sendNotification(region.getUniqueId(), region.getId1().toString(),
                    region.getId2().toString(), region.getId3().toString(), application.isUserAuthenticated(), application.getAppUserRoles());
        }
    }

    @Override
    public void didExitRegion(Region region) {
        Log.d(TAG, "Beacon exited region: uuid-->" + region.getId1() + " major-->" + region.getId2() + " minor-->" + region.getId3());
        beaconExitTimes.put(getDictionaryKey(region), System.currentTimeMillis());

        int major = region.getId2().toInt();
        String id = region.getUniqueId();
        beaconNotificationManager.getNotificationManager().cancel(id, major);
    }

    @Override
    public void didDetermineStateForRegion(int i, Region region) {
        Log.d(TAG, "I have just switched from seeing/not seeing beacons");
    }

    public BeaconNotificationManager getBeaconNotificationManager() {
        return beaconNotificationManager;
    }

    public void stopBeaconManager() {
        if (currRegions.size() > 0) {
            for (int j = 0; j < currRegions.size(); j++) {
                removeRegionFromBootstrap(currRegions.get(j));
            }
            currRegions = new ArrayList<>();
        }
    }

}
