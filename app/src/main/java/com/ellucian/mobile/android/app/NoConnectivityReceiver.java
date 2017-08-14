/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */
package com.ellucian.mobile.android.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;

import com.ellucian.elluciango.R;

public class NoConnectivityReceiver extends BroadcastReceiver {

	private final Activity activity;

	public NoConnectivityReceiver(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(final Context context, Intent incomingIntent) {
        String tag = activity.getClass().getName();
        Log.d(tag, "onReceive, NoConnectivityReceiver");

        if (activity instanceof EllucianActivity) {
            final Snackbar snackbar = Snackbar.make(activity.findViewById(android.R.id.content),
                    R.string.no_connectivity,
                    Snackbar.LENGTH_INDEFINITE);

            snackbar.setAction(R.string.snackbar_dismiss, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snackbar.dismiss();
                }
            });
            snackbar.show();
        }

    }
}