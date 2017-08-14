/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */
package com.ellucian.mobile.android.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.client.services.NotificationsIntentService;

public class NotificationUpdateReceiver extends BroadcastReceiver {

	private final DrawerLayoutActivity activity;

	public NotificationUpdateReceiver(DrawerLayoutActivity activity) {
		this.activity = activity;
	}

	@Override
	public void onReceive(Context context, Intent incomingIntent) {
        boolean updateMenuBadgeCount = incomingIntent.getBooleanExtra(NotificationsIntentService.UPDATE_MENU_BADGE_COUNT, false);
        if (updateMenuBadgeCount) {
            EllucianApplication ellucianApp = (EllucianApplication) context.getApplicationContext();
            ellucianApp.resetModuleMenuAdapter();
            activity.configureNavigationDrawer();
        }
	}		
}