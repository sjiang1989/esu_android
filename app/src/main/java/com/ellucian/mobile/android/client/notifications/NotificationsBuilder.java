/*
 * Copyright 2015 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.notifications;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import com.ellucian.mobile.android.client.ContentProviderOperationBuilder;
import com.ellucian.mobile.android.provider.EllucianContract.Notifications;

import java.util.ArrayList;
import java.util.List;

public class NotificationsBuilder extends ContentProviderOperationBuilder<NotificationsResponse> {

    private static final String TAG = NotificationsBuilder.class.getSimpleName();
    private static final String whereIdEquals = String.format("%s = ?", Notifications.NOTIFICATIONS_ID);

	public NotificationsBuilder(Context context) {
		super(context);
	}
	
	@Override
	public ArrayList<ContentProviderOperation> buildOperations(NotificationsResponse model) {
		final ArrayList<ContentProviderOperation> batch = new ArrayList<>();

        List<String> savedNotificationsIdList = new ArrayList<>();
        List<String> newNotificationsIdList = new ArrayList<>();

        // Build list of new notification Ids.
        for (Notification notification : model.notifications) {
            String id;
            if (!TextUtils.isEmpty(notification.id)) {
                id = notification.id;
            } else {
                id = notification.title;
            }
            newNotificationsIdList.add(id);
        }

        // Step 1
        // Loop through existing notification database. If an existing notification isn't
        // in the mobile server response, remove it from the database
        ContentResolver contentResolver = context.getContentResolver();

        Cursor notificationsCursor = contentResolver.query(Notifications.CONTENT_URI,
                new String[] { Notifications.NOTIFICATIONS_ID},
                null,
                null,
                Notifications.DEFAULT_SORT);

        int count = 0;
        if (notificationsCursor != null && notificationsCursor.moveToFirst()) {
            int columnIndex = notificationsCursor.getColumnIndex(Notifications.NOTIFICATIONS_ID);

            do {
                String savedId = notificationsCursor.getString(columnIndex);
                if (!newNotificationsIdList.contains(savedId)) {
                    Log.d(TAG, "Remove notification from database: " + savedId);

                    String[] whereParams = new String[]{savedId};
                    count++;
                    batch.add(ContentProviderOperation
                            .newDelete(Notifications.CONTENT_URI)
                            .withSelection(whereIdEquals, whereParams)
                            .build());
                } else {
                    savedNotificationsIdList.add(savedId);
                    Log.d(TAG, "Notification id already exists in database: " + savedId);
                }
            } while (notificationsCursor.moveToNext());

        } else {
            Log.d(TAG, "No notifications found in the database");
        }
        if (notificationsCursor != null) {
            notificationsCursor.close();
        }

        Log.d(TAG, "" + count + " notifications deleted from database");

        // Step 2
        // Loop through the mobile server response. If the notification isn't already
        // in the database, add it.
        count = 0; // reset to 0
        for (Notification notification : model.notifications) {
			
			String id;
			if (!TextUtils.isEmpty(notification.id)) {
				id = notification.id;
			} else {
				id = notification.title;
			}

            String statuses = null;
            if (notification.statuses != null && notification.statuses.length > 0) {
                statuses = TextUtils.join(",", notification.statuses);
            }

            if (!savedNotificationsIdList.contains(id)) {
                Log.d(TAG, "Add new notification to database: " + id);
                count++;

                batch.add(ContentProviderOperation
                        .newInsert(Notifications.CONTENT_URI)
                        .withValue(Notifications.NOTIFICATIONS_ID, id)
                        .withValue(Notifications.NOTIFICATIONS_TITLE, notification.title)
                        .withValue(Notifications.NOTIFICATIONS_DETAILS, notification.description)
                        .withValue(Notifications.NOTIFICATIONS_HYPERLINK, notification.hyperlink)
                        .withValue(Notifications.NOTIFICATIONS_LINK_LABEL, notification.linkLabel)
                        .withValue(Notifications.NOTIFICATIONS_DATE, notification.noticeDate)
                        .withValue(Notifications.NOTIFICATIONS_SOURCE, notification.source)
                        .withValue(Notifications.NOTIFICATIONS_DISPATCH_DATE, notification.dispatchDate)
                        .withValue(Notifications.NOTIFICATIONS_MOBILE_HEADLINE, notification.mobileHeadline)
                        .withValue(Notifications.NOTIFICATIONS_EXPIRES, notification.expires)
                        .withValue(Notifications.NOTIFICATIONS_PUSH, notification.push ? 1 : 0)
                        .withValue(Notifications.NOTIFICATIONS_MODULE, notification.module ? 1 : 0)
                        .withValue(Notifications.NOTIFICATIONS_STICKY, notification.sticky ? 1 : 0)
                        .withValue(Notifications.NOTIFICATIONS_STATUSES, statuses)
                        .build());
            } else {
                Log.d(TAG, "Update push notification STATUSES in database for ID: " + id);
                if (notification.push) {
                    String[] whereParams = new String[]{id};

                    batch.add(ContentProviderOperation
                            .newUpdate(Notifications.CONTENT_URI)
                            .withSelection(whereIdEquals, whereParams)
                            .withValue(Notifications.NOTIFICATIONS_STATUSES, statuses)
                            .build());
                }
            }

        }

        Log.d(TAG, "" + count + " notifications added to database");

        return batch;
	}
}
