/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.services;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.ellucian.mobile.android.client.MobileClient;
import com.ellucian.mobile.android.client.notifications.NotificationsBuilder;
import com.ellucian.mobile.android.client.notifications.NotificationsResponse;
import com.ellucian.mobile.android.provider.EllucianContract;
import com.ellucian.mobile.android.util.Extra;

import java.util.ArrayList;

public class NotificationsIntentService extends IntentService {
    private static final String TAG = NotificationsIntentService.class.getSimpleName();
	private static final String PARAM_OUT_DATABASE_UPDATED = "updated";
	public static final String ACTION_FINISHED = "com.ellucian.mobile.android.client.services.NotificationsIntentService.action.updated";
    public static final String UPDATE_MENU_BADGE_COUNT = "update_menu_badge_count";

	public NotificationsIntentService() {
		super("NotificationsIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		boolean success = false;
		Log.d(TAG, "handling intent");
		MobileClient client = new MobileClient(this);

		String url = client.addUserToUrl(intent.getStringExtra(Extra.REQUEST_URL));
		NotificationsResponse response = client.getNotifications(url);
        boolean updateMenu = false;

		if (response != null) {
			Log.d(TAG, "Retrieved response from notifications client");
			NotificationsBuilder builder = new NotificationsBuilder(this);
			Log.d(TAG, "Building content provider operations");
			ArrayList<ContentProviderOperation> ops = builder.buildOperations(response);
			Log.d(TAG, "Created " + ops.size() + " operations");
			
			if(ops.size() > 0) {
				Log.d(TAG, "Executing batch.");
				try {
					getContentResolver().applyBatch(
							EllucianContract.CONTENT_AUTHORITY, ops);
					success = true;
				} catch (RemoteException e) {
					Log.e(TAG, "RemoteException applying batch" + e.getLocalizedMessage());
				} catch (OperationApplicationException e) {
					Log.e(TAG, "OperationApplicationException applying batch:" + e.getLocalizedMessage());
				}
				Log.d(TAG, "Batch executed.");
                updateMenu = true; // notification count changed. update menu.
            }
		} else {
			Log.d(TAG, "Response Object was null");
		}


        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_FINISHED);
        broadcastIntent.putExtra(PARAM_OUT_DATABASE_UPDATED, success);
        broadcastIntent.putExtra(UPDATE_MENU_BADGE_COUNT, updateMenu);
        bm.sendBroadcast(broadcastIntent);

    }

}
