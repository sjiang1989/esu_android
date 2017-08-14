/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.notifications;


import android.content.Intent;
import android.os.Bundle;

import com.ellucian.mobile.android.MainActivity;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.util.Extra;

public class LaunchModuleActivity extends EllucianActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra(Extra.MODULE_ID, getIntent().getStringExtra(Extra.MODULE_ID));
        startActivity(mainIntent);
        finish();
    }

}
