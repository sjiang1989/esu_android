/*
 * Copyright 2015 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.ilp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.ModuleType;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.ilp.widget.AssignmentsWidgetService;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.UserUtils;
import com.ellucian.mobile.android.util.Utils;

public class IlpCardActivity extends EllucianActivity {
    private static final String TAG = IlpCardActivity.class.getSimpleName();
	private IlpCardFragment fragment;
    private final Activity activity = this;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_default_frame);

        if (TextUtils.isEmpty(moduleName)) {
            // When coming from Widget, moduleName is not known.
            String title = PreferencesUtils.getStringFromPreferences(getApplicationContext(), Utils.CONFIGURATION, Utils.ILP_NAME, null);
            setTitle(title);
        } else {
            setTitle(moduleName);
        }

        if (getIntent().getBooleanExtra(AssignmentsWidgetService.LAUNCHED_FROM_APPWIDGET, false)) {
            // Click event on AppWidget started this activity
            sendEvent(GoogleAnalyticsConstants.CATEGORY_WIDGET, GoogleAnalyticsConstants.ACTION_LIST_SELECT, "Assignments", null, "AssignmentsWidgetProvider" );
            getIntent().removeExtra(AssignmentsWidgetService.LAUNCHED_FROM_APPWIDGET);
        }

        UserUtils.manageFingerprintTimeout(this);

        EllucianApplication app = getEllucianApp();
        if(!app.isUserAuthenticated()) {
            Log.d(TAG, "User not authenticated. Request authentication.");
            Utils.showLoginForQueuedIntent(this, moduleId, ModuleType.ILP);
        } else if (app.isFingerprintUpdateNeeded()) {
            Log.d(TAG, "Updated Fingerprint needed.");
            Utils.showLoginForQueuedIntent(this, moduleId, ModuleType.ILP);
        } else if (getIntent().getBooleanExtra(IlpListActivity.SHOW_DETAIL, false)) {
            Intent intent = new Intent();
            intent.setClass(this, IlpListActivity.class);
            intent.putExtras(getIntent().getExtras());
            // make sure to clear the request to show the detail after past on
            getIntent().removeExtra(IlpListActivity.SHOW_DETAIL);
            startActivity(intent);
        }

		FragmentManager manager = getSupportFragmentManager();
		FragmentTransaction transaction = manager.beginTransaction();
		fragment =  (IlpCardFragment) manager.findFragmentByTag("IlpCardFragment");

		if (fragment == null) {
			fragment = new IlpCardFragment();
			Bundle args = new Bundle();
			args.putString(Extra.COURSES_ILP_URL, getIntent().getStringExtra(Extra.COURSES_ILP_URL));
			fragment.setArguments(args);
			transaction.add(R.id.frame, fragment, "IlpCardFragment");
		} else {
			transaction.attach(fragment);
		}

		transaction.commit();

	}

}
