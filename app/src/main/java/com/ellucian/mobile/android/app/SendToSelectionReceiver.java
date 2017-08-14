// Copyright 2014 Ellucian Company L.P and its affiliates.
package com.ellucian.mobile.android.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.Utils;

public class SendToSelectionReceiver extends BroadcastReceiver {
	
	private final Activity activity;

	public SendToSelectionReceiver(Activity activity) {
		this.activity = activity;
	}
	
	@Override
	public void onReceive(final Context context, Intent incomingIntent) {
		String tag = activity.getClass().getName();
		Log.d(tag, "onReceive, SendToSelectionReceiver");

        Utils.hideProgressIndicator(activity);
        PreferencesUtils.removeValuesFromPreferences(context, Utils.CONFIGURATION, Utils.CONFIGURATION_URL);

		// launch an Activity to allow the use of an AlertDialog
		Intent i = new Intent(context, SendToSelectionReceiverActivity.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(i);
	}
}