/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.registration;

import android.content.Context;

import static com.ellucian.mobile.android.registration.RegistrationActivity.REGISTERED_TAB_INDEX;


class RegistrationRegisteredRecyclerAdapter extends RegistrationRecyclerAdapter {
    private static final String TAG = RegistrationRegisteredRecyclerAdapter.class.getSimpleName();

    RegistrationRegisteredRecyclerAdapter(Context context) {
        super(context, REGISTERED_TAB_INDEX);
    }


}
