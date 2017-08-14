/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.locations;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.notifications.LaunchModuleActivity;
import com.ellucian.mobile.android.provider.EllucianContract;
import com.ellucian.mobile.android.provider.EllucianContract.Modules;
import com.ellucian.mobile.android.provider.EllucianContract.ModulesBeacons;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BeaconNotificationManager {
    static Application application;
    private static NotificationManager notificationManager;
    private static final String MODULE_ROLE_EVERYONE = "Everyone";
    private static final String BEACON_NOTIFICATIONS = "beacon_notifications";

    private static final long DAY_IN_MILLISECS = 86400000;

    public BeaconNotificationManager(Application application) {
        this.application = application;
        notificationManager = (NotificationManager) application.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    static public void sendNotification(String moduleId, String uuid, String major, String minor, Boolean userLoggedIn, List<String> userRoles) {
        final ContentResolver contentResolver = application.getContentResolver();
        String beaconSelection = "(" + Modules.MODULES_ID + " = '" + moduleId + "') AND + (" + ModulesBeacons.MODULES_BEACONS_UUID + " = '" + uuid.toUpperCase() +
                "') AND + (" + ModulesBeacons.MODULES_BEACONS_MAJOR + " = '" + major + "') AND + (" +
                ModulesBeacons.MODULES_BEACONS_MINOR + " = '" + minor + "')";

        Cursor beaconCursor = contentResolver.query(ModulesBeacons.CONTENT_URI,
                new String[]{ModulesBeacons.MODULES_BEACONS_MESSAGE},
                beaconSelection, null, ModulesBeacons.DEFAULT_SORT);

        if (beaconCursor != null && beaconCursor.moveToFirst()) {
            int messageIndex = beaconCursor.getColumnIndex(ModulesBeacons.MODULES_BEACONS_MESSAGE);

            Cursor modulesCursor = getModulesCursor(moduleId);
            if (modulesCursor.moveToFirst()) {
                int showIndex = modulesCursor.getColumnIndex(Modules.MODULE_SHOW_FOR_GUEST);

                if (permissionToLaunch(userLoggedIn, modulesCursor.getString(showIndex), userRoles, getModuleRoles(contentResolver, moduleId), moduleId)) {
                    createNotification(uuid, major, minor, moduleId, beaconCursor.getString(messageIndex));

                    ((EllucianApplication) application).sendEvent(GoogleAnalyticsConstants.CATEGORY_LOCATIONS, GoogleAnalyticsConstants.ACTION_NOTIFY, "User notified of beacon", null, "BeaconNotificationManager");
                }
            }
            modulesCursor.close();
            beaconCursor.close();
        }
    }

    private static void createNotification(String uuid, String major, String minor, String modulesId, String message) {
        final Bitmap largeNotificationIcon = BitmapFactory.decodeResource(application.getResources(), R.mipmap.ic_launcher);
        int notificationIcon = R.drawable.ic_notifications;

        Random randomGenerator = new Random();

        Intent muteIntent = new Intent("com.ellucian.mobile.android.client.locations.BeaconNotificationManager.MUTE_BEACON");
        muteIntent.putExtra("uuid", uuid);
        muteIntent.putExtra("major", major);
        muteIntent.putExtra("minor", minor);
        muteIntent.putExtra("modulesId", modulesId);
        muteIntent.putExtra("tempMute", false);
        PendingIntent resultPendingIntent = PendingIntent.getBroadcast(application, randomGenerator.nextInt(), muteIntent, 0);

        Intent tempMuteIntent = new Intent("com.ellucian.mobile.android.client.locations.BeaconNotificationManager.MUTE_BEACON");
        tempMuteIntent.putExtra("uuid", uuid);
        tempMuteIntent.putExtra("major", major);
        tempMuteIntent.putExtra("minor", minor);
        tempMuteIntent.putExtra("modulesId", modulesId);
        tempMuteIntent.putExtra("tempMute", true);
        PendingIntent resultPendingIntent2 = PendingIntent.getBroadcast(application, randomGenerator.nextInt(), tempMuteIntent, 0);

        NotificationCompat.Action wearableAction =
                new NotificationCompat.Action.Builder(R.drawable.ic_not_interested_white,
                        application.getResources().getText(R.string.location_notification_action_mute),
                        resultPendingIntent).build();
        NotificationCompat.Action wearableAction2 =
                new NotificationCompat.Action.Builder(R.drawable.ic_not_interested_white,
                        application.getString(R.string.location_notification_action_mute_for_today),
                        resultPendingIntent2).build();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(application)
                        .setContentTitle(application.getResources().getText(R.string.notifications_device_title))
                        .setContentText(message)
                        .setSmallIcon(notificationIcon)
                        .setLargeIcon(largeNotificationIcon)
                        .setDefaults(Notification.DEFAULT_VIBRATE)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setGroup(BEACON_NOTIFICATIONS)
                        .setAutoCancel(true)
                        .extend(new NotificationCompat.WearableExtender().addAction(wearableAction).addAction(wearableAction2))
                        .addAction(R.drawable.empty_image,
                                application.getResources().getText(R.string.location_notification_action_mute_for_today),
                                resultPendingIntent2)
                        .addAction(R.drawable.empty_image,
                                application.getResources().getText(R.string.location_notification_action_mute),
                                resultPendingIntent);


        Intent launchModuleIntent = new Intent(application, LaunchModuleActivity.class);
        launchModuleIntent.putExtra(Extra.MODULE_ID, modulesId);
        PendingIntent launchModulePi = PendingIntent.getActivity(application, randomGenerator.nextInt(), launchModuleIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(launchModulePi);

        notificationManager.notify(modulesId, Integer.parseInt(major), builder.build());
    }

    private static Cursor getModulesCursor (String module) {
        final ContentResolver contentResolver = application.getContentResolver();
        String modulesSelection =  Modules.MODULES_ID + " = '" + module + "'";
        return contentResolver.query(Modules.CONTENT_URI,
                new String[]{Modules.MODULE_TYPE, Modules.MODULES_ICON_URL,
                        Modules.MODULE_SHOW_FOR_GUEST}, modulesSelection, null, Modules.DEFAULT_SORT);

    }

    private static Boolean permissionToLaunch (Boolean userLoggedIn, String hideBeforeLogin, List<String> userRoles, List<String> moduleRoles, String moduleId) {
        if (userLoggedIn) {
            if (moduleRoles != null) {
                if (moduleRoles.size() == 0) { //3.0 upgrade compatibility
                    return !isTempMuted(moduleId);
                } else if (moduleRoles.contains(MODULE_ROLE_EVERYONE)) {
                    return !isTempMuted(moduleId);
                } else if (userRoles != null) {
                    return doesUserHaveAccessForRole(userRoles, moduleRoles) && !isTempMuted(moduleId);
                }
            }
        } else {
            return !hideBeforeLogin.equals("0") && !isTempMuted(moduleId);
        }
        return false;
    }

    private static Boolean isTempMuted(String moduleId) {
        String muteInfo = PreferencesUtils.getStringFromPreferences(application, Utils.MUTE_LOCATIONS, moduleId, "true");
        if (!muteInfo.equals("true")) {
            if (System.currentTimeMillis() - Long.valueOf(muteInfo) >= DAY_IN_MILLISECS) {
                PreferencesUtils.removeValuesFromPreferences(application, Utils.MUTE_LOCATIONS, moduleId);
                return false;
            } else { return true; }
        }
        return false;
    }

    private static List<String> getModuleRoles(ContentResolver resolver, String moduleId) {
        List<String> roles = new ArrayList<>();
        Cursor moduleRolesCursor = resolver.query(EllucianContract.ModulesRoles.CONTENT_URI,
                new String[] {EllucianContract.ModulesRoles.MODULE_ROLES_NAME },
                Modules.MODULES_ID + " = ?",
                new String[] {moduleId},
                EllucianContract.ModulesRoles.DEFAULT_SORT);
        if (moduleRolesCursor != null) {
            while (moduleRolesCursor.moveToNext()) {
                roles.add(moduleRolesCursor.getString(
                        moduleRolesCursor.getColumnIndex(EllucianContract.ModulesRoles.MODULE_ROLES_NAME)));
            }
            moduleRolesCursor.close();
        }
        return roles;
    }

    private static boolean doesUserHaveAccessForRole(List<String> userRoles, List<String> moduleRoles) {
        if (userRoles == null || moduleRoles == null) {
            return false;
        }
        for (String userRole : userRoles) {
            if (moduleRoles.contains(userRole)) {
                return true;
            }
        }
        return false;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

}
